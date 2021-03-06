/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vfs;

import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.io.URLUtil;
import com.intellij.util.lang.UrlClassLoader;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;

public class VfsUtil extends VfsUtilCore {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.VfsUtil");
  public static final char VFS_PATH_SEPARATOR = '/';

  public static final String LOCALHOST_URI_PATH_PREFIX = "localhost/";

  public static void saveText(@NotNull VirtualFile file, @NotNull String text) throws IOException {
    Charset charset = file.getCharset();
    file.setBinaryContent(text.getBytes(charset.name()));
  }

  /**
   * Copies all files matching the <code>filter</code> from <code>fromDir</code> to <code>toDir</code>.
   * Symlinks end special files are ignored.
   *
   * @param requestor any object to control who called this method. Note that
   *                  it is considered to be an external change if <code>requestor</code> is <code>null</code>.
   *                  See {@link VirtualFileEvent#getRequestor}
   * @param fromDir   the directory to copy from
   * @param toDir     the directory to copy to
   * @param filter    {@link VirtualFileFilter}
   * @throws IOException if files failed to be copied
   */
  public static void copyDirectory(Object requestor,
                                   @NotNull VirtualFile fromDir,
                                   @NotNull VirtualFile toDir,
                                   @Nullable VirtualFileFilter filter) throws IOException {
    @SuppressWarnings("UnsafeVfsRecursion") VirtualFile[] children = fromDir.getChildren();
    for (VirtualFile child : children) {
      if (!child.is(VFileProperty.SYMLINK) && !child.is(VFileProperty.SPECIAL) && (filter == null || filter.accept(child))) {
        if (!child.isDirectory()) {
          copyFile(requestor, child, toDir);
        }
        else {
          VirtualFile newChild = toDir.findChild(child.getName());
          if (newChild == null) {
            newChild = toDir.createChildDirectory(requestor, child.getName());
          }
          copyDirectory(requestor, child, newChild, filter);
        }
      }
    }
  }

  /**
   * Copies content of resource to the given file
   *
   * @param file        to copy to
   * @param resourceUrl url of the resource to be copied
   * @throws java.io.IOException if resource not found or copying failed
   */
  public static void copyFromResource(@NotNull VirtualFile file, @NonNls @NotNull String resourceUrl) throws IOException {
    InputStream out = VfsUtil.class.getResourceAsStream(resourceUrl);
    if (out == null) {
      throw new FileNotFoundException(resourceUrl);
    }
    try {
      byte[] bytes = FileUtil.adaptiveLoadBytes(out);
      file.setBinaryContent(bytes);
    }
    finally {
      out.close();
    }
  }

  /**
   * Makes a copy of the <code>file</code> in the <code>toDir</code> folder and returns it.
   * Handles both files and directories.
   *
   * @param requestor any object to control who called this method. Note that
   *                  it is considered to be an external change if <code>requestor</code> is <code>null</code>.
   *                  See {@link VirtualFileEvent#getRequestor}
   * @param file      file or directory to make a copy of
   * @param toDir     directory to make a copy in
   * @return a copy of the file
   * @throws IOException if file failed to be copied
   */
  public static VirtualFile copy(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile toDir) throws IOException {
    if (file.isDirectory()) {
      VirtualFile newDir = toDir.createChildDirectory(requestor, file.getName());
      copyDirectory(requestor, file, newDir, null);
      return newDir;
    }
    else {
      return copyFile(requestor, file, toDir);
    }
  }

  /**
   * Gets the array of common ancestors for passed files.
   *
   * @param files array of files
   * @return array of common ancestors for passed files
   */
  @NotNull
  public static VirtualFile[] getCommonAncestors(@NotNull VirtualFile[] files) {
    // Separate files by first component in the path.
    HashMap<VirtualFile, Set<VirtualFile>> map = new HashMap<VirtualFile, Set<VirtualFile>>();
    for (VirtualFile aFile : files) {
      VirtualFile directory = aFile.isDirectory() ? aFile : aFile.getParent();
      if (directory == null) return VirtualFile.EMPTY_ARRAY;
      VirtualFile[] path = getPathComponents(directory);
      Set<VirtualFile> filesSet;
      final VirtualFile firstPart = path[0];
      if (map.containsKey(firstPart)) {
        filesSet = map.get(firstPart);
      }
      else {
        filesSet = new THashSet<VirtualFile>();
        map.put(firstPart, filesSet);
      }
      filesSet.add(directory);
    }
    // Find common ancestor for each set of files.
    ArrayList<VirtualFile> ancestorsList = new ArrayList<VirtualFile>();
    for (Set<VirtualFile> filesSet : map.values()) {
      VirtualFile ancestor = null;
      for (VirtualFile file : filesSet) {
        if (ancestor == null) {
          ancestor = file;
          continue;
        }
        ancestor = getCommonAncestor(ancestor, file);
        //assertTrue(ancestor != null);
      }
      ancestorsList.add(ancestor);
      filesSet.clear();
    }
    return toVirtualFileArray(ancestorsList);
  }

  /**
   * Gets the common ancestor for passed files, or null if the files do not have common ancestors.
   *
   * @param file1 fist file
   * @param file2 second file
   * @return common ancestor for the passed files. Returns <code>null</code> if
   *         the files do not have common ancestor
   */
  @Nullable
  public static VirtualFile getCommonAncestor(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
    if (!file1.getFileSystem().equals(file2.getFileSystem())) {
      return null;
    }

    VirtualFile[] path1 = getPathComponents(file1);
    VirtualFile[] path2 = getPathComponents(file2);

    int lastEqualIdx = -1;
    for (int i = 0; i < path1.length && i < path2.length; i++) {
      if (path1[i].equals(path2[i])) {
        lastEqualIdx = i;
      }
      else {
        break;
      }
    }
    return lastEqualIdx == -1 ? null : path1[lastEqualIdx];
  }

  /**
   * Gets the common ancestor for passed files, or {@code null} if the files do not have common ancestors.
   */
  @Nullable
  public static VirtualFile getCommonAncestor(@NotNull Collection<? extends VirtualFile> files) {
    VirtualFile ancestor = null;
    for (VirtualFile file : files) {
      if (ancestor == null) {
        ancestor = file;
      }
      else {
        ancestor = getCommonAncestor(ancestor, file);
        if (ancestor == null) return null;
      }
    }
    return ancestor;
  }

  @Nullable
  public static VirtualFile findRelativeFile(@Nullable VirtualFile base, String... path) {
    VirtualFile file = base;

    for (String pathElement : path) {
      if (file == null) return null;
      if ("..".equals(pathElement)) {
        file = file.getParent();
      }
      else {
        file = file.findChild(pathElement);
      }
    }

    return file;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  @Nullable
  public static VirtualFile findRelativeFile(@NotNull String uri, @Nullable VirtualFile base) {
    if (base != null) {
      if (!base.isValid()) {
        LOG.error("Invalid file name: " + base.getName() + ", url: " + uri);
      }
    }

    uri = uri.replace('\\', '/');

    if (uri.startsWith("file:///")) {
      uri = uri.substring("file:///".length());
      if (!SystemInfo.isWindows) uri = "/" + uri;
    }
    else if (uri.startsWith("file:/")) {
      uri = uri.substring("file:/".length());
      if (!SystemInfo.isWindows) uri = "/" + uri;
    }
    else if (uri.startsWith("file:")) {
      uri = uri.substring("file:".length());
    }

    VirtualFile file = null;

    if (uri.startsWith("jar:file:/")) {
      uri = uri.substring("jar:file:/".length());
      if (!SystemInfo.isWindows) uri = "/" + uri;
      file = VirtualFileManager.getInstance().findFileByUrl(JarFileSystem.PROTOCOL_PREFIX + uri);
    }
    else {
      if (!SystemInfo.isWindows && StringUtil.startsWithChar(uri, '/')) {
        file = LocalFileSystem.getInstance().findFileByPath(uri);
      }
      else if (SystemInfo.isWindows && uri.length() >= 2 && Character.isLetter(uri.charAt(0)) && uri.charAt(1) == ':') {
        file = LocalFileSystem.getInstance().findFileByPath(uri);
      }
    }

    if (file == null && uri.contains(ArchiveFileSystem.ARCHIVE_SEPARATOR)) {
      file = StandardFileSystems.jar().findFileByPath(uri);
      if (file == null && base == null) {
        file = VirtualFileManager.getInstance().findFileByUrl(uri);
      }
    }

    if (file == null) {
      if (base == null) return LocalFileSystem.getInstance().findFileByPath(uri);
      if (!base.isDirectory()) base = base.getParent();
      if (base == null) return LocalFileSystem.getInstance().findFileByPath(uri);
      file = VirtualFileManager.getInstance().findFileByUrl(base.getUrl() + "/" + uri);
      if (file == null) return null;
    }

    return file;
  }

  @NonNls private static final String MAILTO = "mailto";
  private static final String PROTOCOL_DELIMITER = ":";

  /**
   * Searches for the file specified by given java,net.URL.
   * Note that this method currently tested only for "file" and "jar" protocols under Unix and Windows
   *
   * @param url the URL to find file by
   * @return <code>{@link VirtualFile}</code> if the file was found, <code>null</code> otherwise
   */
  public static VirtualFile findFileByURL(@NotNull URL url) {
    VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();
    return findFileByURL(url, virtualFileManager);
  }

  public static VirtualFile findFileByURL(@NotNull URL url, @NotNull VirtualFileManager virtualFileManager) {
    String vfUrl = convertFromUrl(url);
    return virtualFileManager.findFileByUrl(vfUrl);
  }

  @Nullable
  public static VirtualFile findFileByIoFile(@NotNull File file, boolean refreshIfNeeded) {
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    VirtualFile virtualFile = fileSystem.findFileByIoFile(file);
    if (virtualFile == null && refreshIfNeeded) {
      virtualFile = fileSystem.refreshAndFindFileByIoFile(file);
    }
    return virtualFile;
  }

  /**
   * Converts VsfUrl info java.net.URL. Does not support "jar:" protocol.
   *
   * @param vfsUrl VFS url (as constructed by VfsFile.getUrl())
   * @return converted URL or null if error has occured
   */

  @Nullable
  public static URL convertToURL(@NotNull String vfsUrl) {
    if (vfsUrl.startsWith(StandardFileSystems.JAR_PROTOCOL)) {
      LOG.error("jar: protocol not supported.");
      return null;
    }

    // [stathik] for supporting mail URLs in Plugin Manager
    if (vfsUrl.startsWith(MAILTO)) {
      try {
        return new URL(vfsUrl);
      }
      catch (MalformedURLException e) {
        return null;
      }
    }

    String[] split = vfsUrl.split("://");

    if (split.length != 2) {
      LOG.debug("Malformed VFS URL: " + vfsUrl);
      return null;
    }

    String protocol = split[0];
    String path = split[1];

    try {
      if (protocol.equals(StandardFileSystems.FILE_PROTOCOL)) {
        return new URL(StandardFileSystems.FILE_PROTOCOL, "", path);
      }
      else {
        return UrlClassLoader.internProtocol(new URL(vfsUrl));
      }
    }
    catch (MalformedURLException e) {
      LOG.debug("MalformedURLException occurred:" + e.getMessage());
      return null;
    }
  }

  @NotNull
  public static String convertFromUrl(@NotNull URL url) {
    String protocol = url.getProtocol();
    String path = url.getPath();
    if (protocol.equals(StandardFileSystems.JAR_PROTOCOL)) {
      if (StringUtil.startsWithConcatenation(path, StandardFileSystems.FILE_PROTOCOL, PROTOCOL_DELIMITER)) {
        try {
          URL subURL = new URL(path);
          path = subURL.getPath();
        }
        catch (MalformedURLException e) {
          throw new RuntimeException(VfsBundle.message("url.parse.unhandled.exception"), e);
        }
      }
      else {
        throw new RuntimeException(new IOException(VfsBundle.message("url.parse.error", url.toExternalForm())));
      }
    }
    if (SystemInfo.isWindows || SystemInfo.isOS2) {
      while (!path.isEmpty() && path.charAt(0) == '/') {
        path = path.substring(1, path.length());
      }
    }

    path = URLUtil.unescapePercentSequences(path);
    return protocol + "://" + path;
  }

  public static VirtualFile copyFileRelative(Object requestor,
                                             @NotNull VirtualFile file,
                                             @NotNull VirtualFile toDir,
                                             @NotNull String relativePath) throws IOException {
    StringTokenizer tokenizer = new StringTokenizer(relativePath, "/");
    VirtualFile curDir = toDir;

    while (true) {
      String token = tokenizer.nextToken();
      if (tokenizer.hasMoreTokens()) {
        VirtualFile childDir = curDir.findChild(token);
        if (childDir == null) {
          childDir = curDir.createChildDirectory(requestor, token);
        }
        curDir = childDir;
      }
      else {
        return copyFile(requestor, file, curDir, token);
      }
    }
  }

  @NotNull
  public static String fixIDEAUrl(@NotNull String ideaUrl) {
    int idx = ideaUrl.indexOf("://");
    if (idx >= 0) {
      String s = ideaUrl.substring(0, idx);

      if (s.equals(JarFileSystem.PROTOCOL)) {
        //noinspection HardCodedStringLiteral
        s = "jar:file";
      }
      ideaUrl = s + ":/" + ideaUrl.substring(idx + 3);
    }
    return ideaUrl;
  }

  @NotNull
  public static String fixURLforIDEA(@NotNull String url) {
    // removeLocalhostPrefix - false due to backward compatibility reasons
    return toIdeaUrl(url, false);
  }

  @NotNull
  public static String toIdeaUrl(@NotNull String url) {
    return toIdeaUrl(url, true);
  }

  @NotNull
  public static String toIdeaUrl(@NotNull String url, boolean removeLocalhostPrefix) {
    int index = url.indexOf(":/");
    if (index < 0 || (index + 2) >= url.length()) {
      return url;
    }

    if (url.charAt(index + 2) != '/') {
      String prefix = url.substring(0, index);
      String suffix = url.substring(index + 2);

      if (SystemInfoRt.isWindows) {
        return prefix + "://" + suffix;
      }
      else if (removeLocalhostPrefix && prefix.equals(StandardFileSystems.FILE_PROTOCOL) && suffix.startsWith(LOCALHOST_URI_PATH_PREFIX)) {
        // sometimes (e.g. in Google Chrome for Mac) local file url is prefixed with 'localhost' so we need to remove it
        return prefix + ":///" + suffix.substring(LOCALHOST_URI_PATH_PREFIX.length());
      }
      else {
        return prefix + ":///" + suffix;
      }
    }
    else if (url.charAt(index + 3) == '/' &&
             SystemInfoRt.isWindows &&
             url.regionMatches(0, LocalFileSystem.PROTOCOL_PREFIX, 0, LocalFileSystem.PROTOCOL_PREFIX.length())) {
      // file:///C:/test/file.js -> file://C:/test/file.js
      for (int i = index + 4; i < url.length(); i++) {
        char c = url.charAt(i);
        if (c == '/') {
          break;
        }
        else if (c == ':') {
          return LocalFileSystem.PROTOCOL_PREFIX + url.substring(index + 4);
        }
      }
      return url;
    }
    return url;
  }

  /**
   * @return correct URL, must be used only for external communication
   */
  @NotNull
  public static URI toUri(@NotNull VirtualFile file) {
    String path = file.getPath();
    try {
      if (file.isInLocalFileSystem()) {
        if (SystemInfo.isWindows && path.charAt(0) != '/') {
          path = '/' + path;
        }
        return new URI(file.getFileSystem().getProtocol(), "", path, null, null);
      }
      return new URI(file.getFileSystem().getProtocol(), path, null);
    }
    catch (URISyntaxException e) {
      throw new IllegalArgumentException(e);
    }
  }

  /**
   * @return correct URL, must be used only for external communication
   */
  @NotNull
  public static URI toUri(@NotNull File file) {
    String path = file.toURI().getPath();
    try {
      if (SystemInfo.isWindows && path.charAt(0) != '/') {
        path = '/' + path;
      }
      return new URI(StandardFileSystems.FILE_PROTOCOL, "", path, null, null);
    }
    catch (URISyntaxException e) {
      throw new IllegalArgumentException(e);
    }
  }

  /**
   * uri - may be incorrect (escaping or missed "/" before disk name under windows), may be not fully encoded,
   * may contains query and fragment
   *
   * @return correct URI, must be used only for external communication
   */
  @Nullable
  public static URI toUri(@NonNls @NotNull String uri) {
    int index = uri.indexOf("://");
    if (index < 0) {
      // true URI, like mailto:
      try {
        return new URI(uri);
      }
      catch (URISyntaxException e) {
        LOG.debug(e);
        return null;
      }
    }

    if (SystemInfo.isWindows && uri.startsWith(LocalFileSystem.PROTOCOL_PREFIX)) {
      int firstSlashIndex = index + "://".length();
      if (uri.charAt(firstSlashIndex) != '/') {
        uri = LocalFileSystem.PROTOCOL_PREFIX + '/' + uri.substring(firstSlashIndex);
      }
    }

    try {
      return new URI(uri);
    }
    catch (URISyntaxException e) {
      LOG.debug("uri is not fully encoded", e);
      // so, uri is not fully encoded (space)
      try {
        int fragmentIndex = uri.lastIndexOf('#');
        String path = uri.substring(index + 1, fragmentIndex > 0 ? fragmentIndex : uri.length());
        String fragment = fragmentIndex > 0 ? uri.substring(fragmentIndex + 1) : null;
        return new URI(uri.substring(0, index), path, fragment);
      }
      catch (URISyntaxException e1) {
        LOG.debug(e1);
        return null;
      }
    }
  }

  /**
   * Returns the relative path from one virtual file to another.
   *
   * @param src           the file from which the relative path is built.
   * @param dst           the file to which the path is built.
   * @param separatorChar the separator for the path components.
   * @return the relative path, or null if the files have no common ancestor.
   * @since 5.0.2
   */

  @Nullable
  public static String getPath(@NotNull VirtualFile src, @NotNull VirtualFile dst, char separatorChar) {
    final VirtualFile commonAncestor = getCommonAncestor(src, dst);
    if (commonAncestor != null) {
      StringBuilder buffer = new StringBuilder();
      if (!Comparing.equal(src, commonAncestor)) {
        while (!Comparing.equal(src.getParent(), commonAncestor)) {
          buffer.append("..").append(separatorChar);
          src = src.getParent();
        }
      }
      buffer.append(getRelativePath(dst, commonAncestor, separatorChar));
      return buffer.toString();
    }

    return null;
  }

  public static String getUrlForLibraryRoot(@NotNull File libraryRoot) {
    String path = FileUtil.toSystemIndependentName(libraryRoot.getAbsolutePath());
    final FileType fileTypeByFileName = FileTypeManager.getInstance().getFileTypeByFileName(libraryRoot.getName());
    if (fileTypeByFileName instanceof ArchiveFileType) {

      final String protocol = ((ArchiveFileType)fileTypeByFileName).getProtocol();

      return VirtualFileManager.constructUrl(protocol, path + ArchiveFileSystem.ARCHIVE_SEPARATOR);
    }
    else {
      return VirtualFileManager.constructUrl(LocalFileSystem.getInstance().getProtocol(), path);
    }
  }

  public static VirtualFile createChildSequent(Object requestor,
                                               @NotNull VirtualFile dir,
                                               @NotNull String prefix,
                                               @NotNull String extension) throws IOException {
    String fileName = prefix + "." + extension;
    int i = 1;
    while (dir.findChild(fileName) != null) {
      fileName = prefix + i + "." + extension;
      i++;
    }
    return dir.createChildData(requestor, fileName);
  }

  @NotNull
  public static String[] filterNames(@NotNull String[] names) {
    int filteredCount = 0;
    for (String string : names) {
      if (isBadName(string)) filteredCount++;
    }
    if (filteredCount == 0) return names;

    String[] result = ArrayUtil.newStringArray(names.length - filteredCount);
    int count = 0;
    for (String string : names) {
      if (isBadName(string)) continue;
      result[count++] = string;
    }

    return result;
  }

  public static boolean isBadName(String name) {
    return name == null || name.isEmpty() || "/".equals(name) || "\\".equals(name);
  }

  public static VirtualFile createDirectories(@NotNull final String dir) throws IOException {
    return new WriteAction<VirtualFile>() {
      @Override
      protected void run(Result<VirtualFile> result) throws Throwable {
        VirtualFile res = createDirectoryIfMissing(dir);
        result.setResult(res);
      }
    }.execute().throwException().getResultObject();
  }

  public static VirtualFile createDirectoryIfMissing(VirtualFile parent, String relativePath) throws IOException {
    for (String each : StringUtil.split(relativePath, "/")) {
      VirtualFile child = parent.findChild(each);
      if (child == null) {
        child = parent.createChildDirectory(LocalFileSystem.getInstance(), each);
      }
      parent = child;
    }
    return parent;
  }

  @Nullable
  public static VirtualFile createDirectoryIfMissing(@NotNull String directoryPath) throws IOException {
    directoryPath = FileUtil.toSystemIndependentName(directoryPath);
    // Remove last slash in C:/Path/
    // If not remove it - code `parent.createChildDirectory(LocalFileSystem.getInstance(), dirName);`
    // ill throw IOException - name is empty
    if(StringUtil.endsWith(directoryPath, "/")) {
      directoryPath = directoryPath.substring(0, directoryPath.length() - 1);
    }
    return doCreateDirectoriesIfMissing(directoryPath);
  }

  private static VirtualFile doCreateDirectoriesIfMissing(String dir) throws IOException {
    final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(dir);
    if (file == null) {
      int pos = dir.lastIndexOf('/');
      if (pos < 0) return null;
      VirtualFile parent = createDirectoryIfMissing(dir.substring(0, pos));
      if (parent == null) return null;
      final String dirName = dir.substring(pos + 1);
      return parent.createChildDirectory(LocalFileSystem.getInstance(), dirName);
    }
    return file;
  }

  public static void processFileRecursivelyWithoutIgnored(@NotNull final VirtualFile root,
                                                          @NotNull final Processor<VirtualFile> processor) {
    final FileTypeManager ftm = FileTypeManager.getInstance();
    processFilesRecursively(root, processor, new Convertor<VirtualFile, Boolean>() {
      public Boolean convert(final VirtualFile vf) {
        return !ftm.isFileIgnored(vf);
      }
    });
  }

  public static void processFilesRecursively(@NotNull VirtualFile root,
                                             @NotNull Processor<VirtualFile> processor,
                                             @NotNull Convertor<VirtualFile, Boolean> directoryFilter) {
    if (!processor.process(root)) return;

    if (root.isDirectory() && directoryFilter.convert(root)) {
      final LinkedList<VirtualFile[]> queue = new LinkedList<VirtualFile[]>();

      queue.add(root.getChildren());

      do {
        final VirtualFile[] files = queue.removeFirst();

        for (VirtualFile file : files) {
          if (!processor.process(file)) return;
          if (file.isDirectory() && directoryFilter.convert(file)) {
            queue.add(file.getChildren());
          }
        }
      }
      while (!queue.isEmpty());
    }
  }

  public static boolean processFilesRecursively(@NotNull VirtualFile root, @NotNull Processor<VirtualFile> processor) {
    if (!processor.process(root)) return false;

    if (root.isDirectory()) {
      final LinkedList<VirtualFile[]> queue = new LinkedList<VirtualFile[]>();

      queue.add(root.getChildren());

      do {
        final VirtualFile[] files = queue.removeFirst();

        for (VirtualFile file : files) {
          if (!processor.process(file)) return false;
          if (file.isDirectory()) {
            queue.add(file.getChildren());
          }
        }
      }
      while (!queue.isEmpty());
    }

    return true;
  }

  @Nullable
  public static <T> T processInputStream(@NotNull final VirtualFile file, @NotNull Function<InputStream, T> function) {
    InputStream stream = null;
    try {
      stream = file.getInputStream();
      return function.fun(stream);
    }
    catch (IOException e) {
      LOG.error(e);
    }
    finally {
      try {
        if (stream != null) {
          stream.close();
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    return null;
  }

  @NotNull
  public static String getReadableUrl(@NotNull final VirtualFile file) {
    String url = null;
    if (file.isInLocalFileSystem()) {
      url = file.getPresentableUrl();
    }
    if (url == null) {
      url = file.getUrl();
    }
    return url;
  }

  @Nullable
  public static VirtualFile getUserHomeDir() {
    final String path = SystemProperties.getUserHome();
    return LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(path));
  }

  @NotNull
  public static VirtualFile[] getChildren(@NotNull VirtualFile dir) {
    VirtualFile[] children = dir.getChildren();
    return children == null ? VirtualFile.EMPTY_ARRAY : children;
  }

  /**
   * @param url Url for virtual file
   * @return url for parent directory of virtual file
   */
  @Nullable
  public static String getParentDir(@Nullable final String url) {
    if (url == null) {
      return null;
    }
    final int index = url.lastIndexOf(VFS_PATH_SEPARATOR);
    return index < 0 ? null : url.substring(0, index);
  }

  /**
   * @param urlOrPath Url for virtual file
   * @return file name
   */
  @Nullable
  public static String extractFileName(@Nullable final String urlOrPath) {
    if (urlOrPath == null) {
      return null;
    }
    final int index = urlOrPath.lastIndexOf(VFS_PATH_SEPARATOR);
    return index < 0 ? null : urlOrPath.substring(index + 1);
  }

  @NotNull
  public static List<VirtualFile> markDirty(boolean recursive, boolean reloadChildren, VirtualFile... files) {
    List<VirtualFile> list = ContainerUtil.filter(Condition.NOT_NULL, files);
    if (list.isEmpty()) {
      return Collections.emptyList();
    }

    for (VirtualFile file : list) {
      if (reloadChildren) {
        file.getChildren();
      }

      if (file instanceof NewVirtualFile) {
        if (recursive) {
          ((NewVirtualFile)file).markDirtyRecursively();
        }
        else {
          ((NewVirtualFile)file).markDirty();
        }
      }
    }
    return list;
  }

  public static void markDirtyAndRefresh(boolean async, boolean recursive, boolean reloadChildren, VirtualFile... files) {
    List<VirtualFile> list = markDirty(recursive, reloadChildren, files);
    if (list.isEmpty()) return;
    LocalFileSystem.getInstance().refreshFiles(list, async, recursive, null);
  }
}
