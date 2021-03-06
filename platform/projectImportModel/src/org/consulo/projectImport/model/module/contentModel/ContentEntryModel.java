/*
 * Copyright 2013 must-be.org
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
package org.consulo.projectImport.model.module.contentModel;

import org.consulo.projectImport.model.ModelContainer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author VISTALL
 * @since 17:27/19.06.13
 */
public class ContentEntryModel extends ModelContainer {
  private final String myUrl;

  public ContentEntryModel(String url) {
    myUrl = url;
  }

  public void addContentFolder(@NotNull String url, @NotNull ContentFolderTypeModel contentFolderTypeModel) {
    addChild(new ContentFolderModel(url, contentFolderTypeModel));
  }

  @NotNull
  public List<ContentFolderModel> getContentFolders() {
    return findChildren(ContentFolderModel.class);
  }

  @NotNull
  public String getUrl() {
    return myUrl;
  }
}
