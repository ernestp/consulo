/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.updateSettings.impl.DetectedPluginsPanel;
import com.intellij.openapi.util.Couple;
import com.intellij.ui.TableUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: anna
 */
public class PluginsAdvertiserDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#" + PluginsAdvertiserDialog.class.getName());

  private final Collection<Couple<IdeaPluginDescriptor>> myUploadedPlugins;
  private final List<IdeaPluginDescriptor> myAllPlugins;
  private final HashSet<String> mySkippedPlugins = new HashSet<String>();

  PluginsAdvertiserDialog(@Nullable Project project, Collection<Couple<IdeaPluginDescriptor>> plugins, List<IdeaPluginDescriptor> allPlugins) {
    super(project);
    myUploadedPlugins = plugins;
    myAllPlugins = allPlugins;
    setTitle("Choose Plugins to Install");
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    final DetectedPluginsPanel foundPluginsPanel = new DetectedPluginsPanel() {
      @Override
      protected Set<String> getSkippedPlugins() {
        return mySkippedPlugins;
      }
    };

    for (Couple<IdeaPluginDescriptor> uploadedPlugin : myUploadedPlugins) {
      foundPluginsPanel.add(uploadedPlugin);
    }
    TableUtil.ensureSelectionExists(foundPluginsPanel.getEntryTable());
    return foundPluginsPanel;
  }

  @Override
  protected void doOKAction() {
   /* final List<PluginNode> nodes = new ArrayList<PluginNode>();
    for (Couple<IdeaPluginDescriptor> downloader : myUploadedPlugins) {
      if (!mySkippedPlugins.contains(downloader.getSecond().getPluginId().toString())) {
        final PluginNode pluginNode = PluginDownloader.createPluginNode(null, downloader);
        if (pluginNode != null) {
          nodes.add(pluginNode);
        }
      }
    }
    try {
      PluginManagerMain.downloadPlugins(nodes, myAllPlugins, new Consumer<Set<PluginNode>>() {
        @Override
        public void consume(Set<PluginNode> pluginNodes) {
          PluginManagerMain.notifyPluginsWereInstalled(pluginNodes, null);
        }
      }, null);
    }
    catch (IOException e) {
      LOG.error(e);
    }   */
    super.doOKAction();
  }
}
