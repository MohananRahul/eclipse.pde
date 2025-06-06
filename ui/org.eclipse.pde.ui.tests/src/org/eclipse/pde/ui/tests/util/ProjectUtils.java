/*******************************************************************************
 * Copyright (c) 2008, 2022 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Hannes Wellmann - Bug 577629 - Unify project creation/deletion in tests
 *******************************************************************************/
package org.eclipse.pde.ui.tests.util;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.environments.IExecutionEnvironment;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.osgi.util.ManifestElement;
import org.eclipse.pde.core.project.IBundleProjectDescription;
import org.eclipse.pde.core.project.IBundleProjectService;
import org.eclipse.pde.core.project.IPackageExportDescription;
import org.eclipse.pde.core.project.IPackageImportDescription;
import org.eclipse.pde.core.project.IRequiredBundleDescription;
import org.eclipse.pde.core.target.NameVersionDescriptor;
import org.eclipse.pde.internal.core.FeatureModelManager;
import org.eclipse.pde.internal.core.PDECore;
import org.eclipse.pde.internal.core.feature.FeatureChild;
import org.eclipse.pde.internal.core.feature.WorkspaceFeatureModel;
import org.eclipse.pde.internal.core.ifeature.IFeature;
import org.eclipse.pde.internal.core.ifeature.IFeatureChild;
import org.eclipse.pde.internal.core.ifeature.IFeatureImport;
import org.eclipse.pde.internal.core.ifeature.IFeatureModelFactory;
import org.eclipse.pde.internal.core.ifeature.IFeaturePlugin;
import org.eclipse.pde.internal.ui.wizards.IProjectProvider;
import org.eclipse.pde.internal.ui.wizards.feature.AbstractCreateFeatureOperation;
import org.eclipse.pde.internal.ui.wizards.feature.FeatureData;
import org.eclipse.pde.internal.ui.wizards.plugin.NewProjectCreationOperation;
import org.eclipse.pde.internal.ui.wizards.plugin.PluginFieldData;
import org.eclipse.pde.ui.IBundleContentWizard;
import org.eclipse.pde.ui.templates.AbstractNewPluginTemplateWizard;
import org.eclipse.pde.ui.templates.ITemplateSection;
import org.eclipse.pde.ui.tests.project.ProjectCreationTests;
import org.eclipse.pde.ui.tests.runtime.TestUtils;
import org.junit.rules.TestRule;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.Version;
import org.osgi.framework.VersionRange;

/**
 * Utility class for project related operations
 */
public class ProjectUtils {

	/**
	 * Used to create projects
	 */
	static class TestProjectProvider implements IProjectProvider {
		private final String fProjectName;

		TestProjectProvider(String projectName) {
			fProjectName = projectName;
		}

		@Override
		public IPath getLocationPath() {
			return ResourcesPlugin.getWorkspace().getRoot().getLocation();
		}

		@Override
		public IProject getProject() {
			return ResourcesPlugin.getWorkspace().getRoot().getProject(fProjectName);
		}

		@Override
		public String getProjectName() {
			return fProjectName;
		}

	}

	/**
	 * Fake wizard
	 */
	static class TestBundleWizard extends AbstractNewPluginTemplateWizard {

		@Override
		protected void addAdditionalPages() {
		}

		@Override
		public ITemplateSection[] getTemplateSections() {
			return new ITemplateSection[0];
		}

	}

	/**
	 * Constant representing the name of the output directory for a project.
	 * Value is: <code>bin</code>
	 */
	public static final String BIN_FOLDER = "bin";

	/**
	 * Constant representing the name of the source directory for a project.
	 * Value is: <code>src</code>
	 */
	public static final String SRC_FOLDER = "src";

	/**
	 * Create a plugin project with the given name and execution environment.
	 *
	 * @param env
	 *            environment for build path or <code>null</code> if default
	 *            system JRE
	 * @return a new plugin project
	 */
	public static IJavaProject createPluginProject(String projectName, IExecutionEnvironment env) throws Exception {
		PluginFieldData data = new PluginFieldData();
		data.setName(projectName);
		data.setId(projectName);
		data.setLegacy(false);
		data.setHasBundleStructure(true);
		data.setSimple(false);
		data.setProvider("IBM");
		data.setLibraryName(".");
		data.setVersion("1.0.0");
		data.setTargetVersion("3.5");
		data.setOutputFolderName(BIN_FOLDER);
		data.setSourceFolderName(SRC_FOLDER);
		if (env != null) {
			data.setExecutionEnvironment(env.getId());
		}
		data.setDoGenerateClass(true);
		data.setClassname(projectName + ".Activator");
		data.setEnableAPITooling(false);
		data.setRCPApplicationPlugin(false);
		data.setUIPlugin(false);
		IProjectProvider provider = new TestProjectProvider(projectName);
		IBundleContentWizard wizard = new TestBundleWizard();
		NewProjectCreationOperation operation = new NewProjectCreationOperation(data, provider, wizard);
		operation.run(new NullProgressMonitor());
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
		IJavaProject javaProject = JavaCore.create(project);
		TestUtils.waitForJobs("ProjectUtils.createPluginProject " + projectName, 100, 10000);
		return javaProject;
	}

	private static final Set<IProject> IMPORTED_PROJECTS = ConcurrentHashMap.newKeySet();

	public static IProject importTestProject(String path) throws IOException, CoreException {
		URL entry = FileLocator.toFileURL(FrameworkUtil.getBundle(ProjectUtils.class).getEntry(path));
		if (entry == null) {
			throw new IllegalArgumentException(path + " does not exist");
		}
		return importTestProject(entry);
	}

	public static IProject importTestProject(URL entry) throws CoreException {
		IPath projectFile = IPath.fromPortableString(entry.getPath()).append(IProjectDescription.DESCRIPTION_FILE_NAME);
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IProjectDescription projectDescription = workspace.loadProjectDescription(projectFile);
		IProject project = workspace.getRoot().getProject(projectDescription.getName());
		project.create(projectDescription, null);
		project.open(null);
		IMPORTED_PROJECTS.add(project);
		return project;
	}

	public static List<IProject> createWorkspacePluginProjects(
			Map<NameVersionDescriptor, Map<String, String>> pluginProjects) throws CoreException {
		List<IProject> projects = new ArrayList<>();
		for (Entry<NameVersionDescriptor, Map<String, String>> entry : pluginProjects.entrySet()) {
			NameVersionDescriptor bundle = entry.getKey();
			IProject project = createPluginProject(bundle.getId(), bundle.getVersion(), entry.getValue());
			projects.add(project);
		}
		return projects;
	}

	public static IProject createPluginProject(String bundleSymbolicName, String version, Map<String, String> headers)
			throws CoreException {
		String projectName = bundleSymbolicName + version.replace('.', '_');
		IProject project = createPluginProject(projectName, bundleSymbolicName, version,
				(description, projectService) -> {
					headers.forEach((header, value) -> {
						switch (header) {
						case Constants.EXPORT_PACKAGE -> setPackageExports(description, projectService, value);
						case Constants.IMPORT_PACKAGE -> setPackageImports(description, projectService, value);
						case Constants.REQUIRE_BUNDLE -> setRequiredBundles(description, projectService, value);
						default -> throw new IllegalArgumentException("Unsupported header: " + header);
						}
					});
				});
		return project;
	}

	private static void setPackageExports(IBundleProjectDescription project, IBundleProjectService projectService,
			String value) {
		IPackageExportDescription[] exports = parseHeader(Constants.EXPORT_PACKAGE, value, h -> {
			Version version = Version.parseVersion(h.getAttribute(Constants.VERSION_ATTRIBUTE));
			return projectService.newPackageExport(h.getValue(), version, true, List.of());
		}).toArray(IPackageExportDescription[]::new);
		project.setPackageExports(exports);
	}

	private static void setPackageImports(IBundleProjectDescription project, IBundleProjectService projectService,
			String value) {
		var imports = parseHeader(Constants.IMPORT_PACKAGE, value, h -> {
			VersionRange version = Optional.ofNullable(h.getAttribute(Constants.VERSION_ATTRIBUTE))
					.map(VersionRange::valueOf).orElse(null);
			boolean optional = Constants.RESOLUTION_OPTIONAL.equals(h.getDirective(Constants.RESOLUTION_DIRECTIVE));
			return projectService.newPackageImport(h.getValue(), version, optional);
		}).toArray(IPackageImportDescription[]::new);
		project.setPackageImports(imports);
	}

	private static void setRequiredBundles(IBundleProjectDescription project, IBundleProjectService projectService,
			String value) {
		var imports = parseHeader(Constants.REQUIRE_BUNDLE, value, h -> {
			VersionRange bundleVersion = Optional.ofNullable(h.getAttribute(Constants.BUNDLE_VERSION))
					.map(VersionRange::valueOf).orElse(null);
			boolean optional = Constants.RESOLUTION_OPTIONAL.equals(h.getDirective(Constants.RESOLUTION_DIRECTIVE));
			boolean reexport = Constants.VISIBILITY_REEXPORT.equals(h.getDirective(Constants.VISIBILITY_DIRECTIVE));
			return projectService.newRequiredBundle(h.getValue(), bundleVersion, optional, reexport);
		}).toArray(IRequiredBundleDescription[]::new);
		project.setRequiredBundles(imports);
	}

	private static <T> Stream<T> parseHeader(String header, String value, Function<ManifestElement, T> parser) {
		try {
			return Arrays.stream(ManifestElement.parseHeader(header, value)).map(parser);
		} catch (BundleException e) {
			throw new IllegalArgumentException("Invalid header: " + value, e);
		}
	}

	public static IProject createPluginProject(String bundleSymbolicName, String bundleVersion) throws CoreException {
		return createPluginProject(bundleSymbolicName, bundleVersion, Map.of());
	}

	public static IProject createPluginProject(String projectName, String bundleSymbolicName, String version)
			throws CoreException {
		return createPluginProject(projectName, bundleSymbolicName, version, (d, s) -> {
		});
	}

	public static IProject createPluginProject(String projectName, String bundleSymbolicName, String version,
			BiConsumer<IBundleProjectDescription, IBundleProjectService> setup) throws CoreException {
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
		IBundleProjectService bundleProjectService = ProjectCreationTests.getBundleProjectService();
		IBundleProjectDescription description = bundleProjectService.getDescription(project);
		description.setSymbolicName(bundleSymbolicName);
		if (version != null) {
			description.setBundleVersion(Version.parseVersion(version));
		}
		setup.accept(description, bundleProjectService);
		description.apply(null);
		return project;
	}

	// --- workspace project deletion ---

	/**
	 * An (intended) {@link org.junit.ClassRule} that deletes all projects from
	 * the test-workspace before and after all tests are executed.
	 * <p>
	 * The intention is to ensure that the workspace is empty before the first
	 * method (static or not) of a test-class is called as well it is cleared
	 * after the last method has returned.
	 * </p>
	 * <p>
	 * For projects that are imported via {@link #importTestProject(String)} the
	 * content is not deleted, for other projects the content is deleted. This
	 * assumes that imported projects are resources in this Test-plugin while
	 * other projects are virtual and can safely be deleted.
	 * </p>
	 */
	public static final TestRule DELETE_ALL_WORKSPACE_PROJECTS_BEFORE_AND_AFTER = TestUtils.getThrowingTestRule( //
			() -> { // Clean-up garbage of other test-classes
				deleteAllWorkspaceProjects();
				return null;
			}, o -> deleteAllWorkspaceProjects());

	/**
	 * An (intended) {@link org.junit.Rule} that deletes the projects from the
	 * test-workspace , that where created during the test-case execution, after
	 * each test-case.
	 * <p>
	 * The intention is to delete those projects from the workspace that were
	 * created during the execution of a test-case, but to retain those that
	 * existed before (e.g. were created in a static initializer).
	 * </p>
	 * <p>
	 * For projects that are imported via {@link #importTestProject(String)} the
	 * content is not deleted, for other projects the content is deleted. This
	 * assumes that imported projects are resources in this Test-plugin while
	 * other projects are virtual and can safely be deleted.
	 * </p>
	 */
	public static final TestRule DELETE_CREATED_WORKSPACE_PROJECTS_AFTER = TestUtils.getThrowingTestRule( //
			() -> Set.of(ResourcesPlugin.getWorkspace().getRoot().getProjects()), //
			projectsBefore -> deleteWorkspaceProjects(projectsBefore));

	public static void deleteAllWorkspaceProjects() throws CoreException {
		deleteWorkspaceProjects(Set.of());
	}

	private static void deleteWorkspaceProjects(Set<IProject> retainedProjects) throws CoreException {
		IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		for (IProject project : projects) {
			if (!retainedProjects.contains(project)) {
				boolean isImportedProject = IMPORTED_PROJECTS.remove(project);
				// Don't delete content of imported projects because they are
				// resources of this test plug-in and must not be deleted
				project.delete(!isImportedProject, true, null);
			}
		}
		// back-up, should not change anything if everything is properly used
		IMPORTED_PROJECTS.retainAll(retainedProjects);
	}

	// --- Feature project creation and setup ---

	public static interface CoreConsumer<E> {
		void accept(E e) throws CoreException;
	}

	public static void createFeatureProject(String id, String version, CoreConsumer<IFeature> featureSetup)
			throws Exception {
		createFeature(id, version, id + "_" + version.replace('.', '_'), featureSetup);
	}

	static IFeature createFeature(String id, String version, String projectName, CoreConsumer<IFeature> featureSetup)
			throws Exception {
		FeatureData featureData = new FeatureData();
		featureData.id = id;
		featureData.version = version;

		IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
		IProject project = workspaceRoot.getProject(projectName);
		IPath location = workspaceRoot.getLocation().append(project.getName());

		IRunnableWithProgress operation = new AbstractCreateFeatureOperation(project, location, featureData, null) {
			@Override
			protected void configureFeature(IFeature feature, WorkspaceFeatureModel model) throws CoreException {
				featureSetup.accept(feature);
			}

			@Override
			protected void openFeatureEditor(IFile manifestFile) {
				// don't open in headless tests
			}
		};
		operation.run(new NullProgressMonitor());

		flushPendingResourceChangeEvents(project);

		FeatureModelManager featureModelManager = PDECore.getDefault().getFeatureModelManager();
		return featureModelManager.getFeatureModel(project).getFeature();
	}

	private static void flushPendingResourceChangeEvents(IProject project) throws CoreException {
		// The create-feature operation above creates/modifies the feature.xml,
		// but not all resource-change-events generated by the operation are
		// handled when the operation exists. If these change-events are not
		// handled now they are handled asynchronously by the Autobuild-job. The
		// problem is that the Feature-model is reset/reload while processing
		// the changes (which first sets all fields to null/zero and then
		// re-reads them), but the reload is NOT guarded by corresponding locks.
		// So if this happens asynchronously and the model is read inbetween the
		// model state could be inconsistent, which occasionally leads to
		// test-failure. Furthermore the feature-models are only added to the
		// FeatureModelManager while processing the resource-changes.
		// Consequently it has to be ensured that all pending resource change
		// events are properly processed now and in this thread:
		// -> Building the project ensures this
		ResourcesPlugin.getWorkspace().run(m -> project.build(IncrementalProjectBuilder.FULL_BUILD, null), null);
	}

	public static void addRequiredPlugin(IFeature feature, String id, String version, int matchRule)
			throws CoreException {
		addImport(feature, id, version, matchRule, IFeatureImport.PLUGIN);
	}

	public static void addRequiredFeature(IFeature feature, String id, String version, int matchRule)
			throws CoreException {
		addImport(feature, id, version, matchRule, IFeatureImport.FEATURE);
	}

	private static void addImport(IFeature feature, String id, String version, int matchRule, int type)
			throws CoreException {
		IFeatureModelFactory factory = feature.getModel().getFactory();
		IFeatureImport featureImport = factory.createImport();
		featureImport.setId(id);
		featureImport.setVersion(version);
		featureImport.setMatch(matchRule);
		featureImport.setType(type);

		feature.addImports(new IFeatureImport[] { featureImport });
	}

	public static FeatureChild addIncludedFeature(IFeature feature, String id, String version) throws CoreException {
		FeatureChild featureChild = (FeatureChild) feature.getModel().getFactory().createChild();
		featureChild.setId(id);
		featureChild.setVersion(version);
		featureChild.setOptional(false);
		feature.addIncludedFeatures(new IFeatureChild[] { featureChild });
		return featureChild;
	}

	public static IFeaturePlugin addIncludedPlugin(IFeature feature, String id, String version) throws CoreException {
		IFeaturePlugin featurePlugin = feature.getModel().getFactory().createPlugin();
		featurePlugin.setId(id);
		featurePlugin.setVersion(version);
		feature.addPlugins(new IFeaturePlugin[] { featurePlugin });
		return featurePlugin;
	}

}
