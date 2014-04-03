package com.twitter.intellij.pants.service.project;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ModuleDependencyData;
import com.intellij.openapi.module.ModuleTypeId;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.PathUtil;
import com.twitter.intellij.pants.settings.PantsExecutionSettings;
import com.twitter.intellij.pants.util.PantsConstants;
import com.twitter.intellij.pants.util.PantsUtil;

import java.util.*;

public class PantsDependenciesResolver extends PantsResolverBase {

  private Map<String, List<String>> roots = Collections.emptyMap();

  public PantsDependenciesResolver(String projectPath, PantsExecutionSettings settings) {
    super(projectPath, settings);
  }

  @Override
  protected void fillArguments(GeneralCommandLine commandLine) {
    commandLine.addParameter("goal");
    commandLine.addParameter("dependencies");
    for (String targetName : settings.getTargetNames()) {
      if ("".equals(targetName)) {
        commandLine.addParameter(projectPath);
      } else {
        commandLine.addParameter(projectPath + ":" + targetName);
      }
    }
  }

  @Override
  protected void parse(List<String> out, List<String> err) {
    roots = findRoots(out);
  }

  private Map<String, List<String>> findRoots(List<String> out) {
    final VirtualFile buildFile = VirtualFileManager.getInstance().findFileByUrl(VfsUtil.pathToUrl(projectPath));
    if (buildFile == null) {
      throw new ExternalSystemException("Couldn't find BUILD file: " + projectPath);
    }
    final VirtualFile pantsExecutable = PantsUtil.findPantsExecutable(buildFile);
    if (pantsExecutable == null) {
      throw new ExternalSystemException("Couldn't find pants executable for: " + projectPath);
    }

    final String workingDir = pantsExecutable.getParent().getPath();
    final String buildFolder = buildFile.getParent().getPath();

    final Map<String, List<String>> roots = new HashMap<String, List<String>>();
    for (String line : out) {
      final int sepIndex = line.lastIndexOf(':');
      if (sepIndex < 0) continue;
      final String projectPath = line.substring(0, sepIndex);
      final String targetName = line.substring(sepIndex);
      if (!projectPath.endsWith(PantsUtil.BUILD)) continue;

      List<String> targets = roots.get(projectPath);
      if (targets == null) {
        targets = new ArrayList<String>();
        roots.put(projectPath, targets);
      }
      targets.add(targetName);
    }
    return roots;
  }

  @Override
  public void addInfo(DataNode<ModuleData> moduleDataNode) {
    for (Map.Entry<String, List<String>> entry : roots.entrySet()) {
      final String projectPath = entry.getKey();
      final List<String> targets = entry.getValue();

      final String name = projectPath;
      final ModuleData moduleData = new ModuleData(
        name,
        PantsConstants.SYSTEM_ID,
        ModuleTypeId.JAVA_MODULE,
        name,
        PathUtil.getParentPath(projectPath),
        projectPath
      );

      final ModuleDependencyData moduleDependencyData = new ModuleDependencyData(
        moduleDataNode.getData(),
        moduleData
      );

      moduleDataNode.createChild(ProjectKeys.MODULE_DEPENDENCY, moduleDependencyData);
    }
  }
}