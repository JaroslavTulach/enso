package org.enso.tools.enso4igv;

import java.beans.PropertyChangeListener;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Icon;
import javax.swing.event.ChangeListener;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.classpath.GlobalPathRegistry;
import org.netbeans.api.java.platform.JavaPlatform;
import org.netbeans.api.java.queries.SourceForBinaryQuery;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.api.project.Sources;
import org.netbeans.spi.java.classpath.ClassPathProvider;
import org.netbeans.spi.java.classpath.support.ClassPathSupport;
import org.netbeans.spi.java.queries.BinaryForSourceQueryImplementation2;
import org.netbeans.spi.java.queries.CompilerOptionsQueryImplementation;
import org.netbeans.spi.java.queries.SourceForBinaryQueryImplementation2;
import org.netbeans.spi.java.queries.SourceLevelQueryImplementation2;
import org.netbeans.spi.project.support.GenericSources;
import org.netbeans.spi.project.ui.ProjectOpenedHook;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.URLMapper;
import org.openide.util.ImageUtilities;

final class ElideClassPathProvider extends ProjectOpenedHook
  implements ClassPathProvider, SourceLevelQueryImplementation2, CompilerOptionsQueryImplementation,
  Sources, BinaryForSourceQueryImplementation2<ElideClassPathProvider.SrcGroup>, SourceForBinaryQueryImplementation2 {

  private static final Logger LOG = Logger.getLogger(SrcGroup.class.getName());
  private static final String BOOT = "classpath/boot";
  private static final String SOURCE = "classpath/source";
  private static final String COMPILE = "classpath/compile";
  private static final String MODULES_COMPILE = "modules/compile";
  private final ElideProject project;
  private final SourceGroup[] sources;

  ElideClassPathProvider(ElideProject prj) {
    this.project = prj;
    this.sources = computeClassPath(prj);
  }

  @Override
  public ClassPath findClassPath(FileObject file, String type) {
    var res = findClassPathImpl(file, type);
    LOG.log(Level.FINE, "findClassPath{0} for {1}  yields {2}", new Object[]{type, file, res});
    return res;
  }

  private ClassPath findClassPathImpl(FileObject file, String type) {
    for (var g : sources) {
      if (g instanceof SrcGroup i && i.controlsSource(file)) {
        var cp = switch (type) {
          case SOURCE ->
            i.srcCp;
          case COMPILE ->
            i.cp;
          case MODULES_COMPILE ->
            i.moduleCp;
          case BOOT ->
            i.platform.getBootstrapLibraries();
          default ->
            null;
        };
        return cp;
      }
    }
    return null;
  }

  @Override
  public void projectOpened() {
    for (var g : sources) {
      if (g instanceof SrcGroup i) {
        GlobalPathRegistry.getDefault().register(COMPILE, new ClassPath[]{i.cp});
        GlobalPathRegistry.getDefault().register(SOURCE, new ClassPath[]{i.srcCp});
      }
    }
  }

  @Override
  public void projectClosed() {
    for (var g : sources) {
      if (g instanceof SrcGroup i) {
        GlobalPathRegistry.getDefault().unregister(COMPILE, new ClassPath[]{i.cp});
        GlobalPathRegistry.getDefault().unregister(SOURCE, new ClassPath[]{i.srcCp});
      }
    }
  }

  private static SourceGroup[] computeClassPath(ElideProject prj) {
    var arr = new ArrayList<SourceGroup>();
    var classes = prj.getProjectDirectory().getFileObject(".dev/jvm/classes");
    if (classes == null) {
        classes = prj.getProjectDirectory().getFileObject(".dev/artifacts/jvm/classes/base/main/");
    }
    var deps = prj.getProjectDirectory().getFileObject((".dev/dependencies/m2"));
    if (classes != null && deps != null) {
        processSrcDir(deps, prj.getProjectDirectory().getFileObject("src"), classes, arr);
        processSrcDir(deps, prj.getProjectDirectory().getFileObject("packages/base/main"), classes, arr);
        processSrcDir(deps, prj.getProjectDirectory().getFileObject("packages/generated/main"), classes, arr);
        processSrcDir(deps, prj.getProjectDirectory().getFileObject(".dev/codegen/jvm/sources/"), classes, arr);
    }
    return arr.toArray(SourceGroup[]::new);
  }

    private static void processSrcDir(FileObject deps, FileObject srcDir, FileObject classes, ArrayList<SourceGroup> arr) {
        if (srcDir == null) {
            return;
        }
        var cpRoots = new ArrayList<FileObject>();
        var en = deps.getChildren(true);
        while (en.hasMoreElements()) {
            var entry = en.nextElement();
            if (entry.isData() && entry.hasExt("jar")) {
                var root = FileUtil.getArchiveRoot(entry);
                cpRoots.add(root);
            }
        }
        var cp = ClassPathSupport.createClassPath(cpRoots.toArray(FileObject[]::new));
        var platform = JavaPlatform.getDefault();
        
        var srcCp = ClassPathSupport.createClassPath(srcDir);
        var out = classes.getFileObject(srcDir.getNameExt());
        var group = new SrcGroup(cp, null, srcCp, platform, out, "25", List.of());
        arr.add(group);
    }

  private static FileObject findProjectFileObject(ElideProject prj, String path) {
    if (path == null) {
      return null;
    }
    if (path.startsWith("./")) {
      return prj.getProjectDirectory().getFileObject(path.substring(2));
    } else {
      return FileUtil.toFileObject(new File(path));
    }
  }

  @Override
  public SourceLevelQueryImplementation2.Result getSourceLevel(FileObject fo) {
    for (var g : sources) {
      if (g instanceof SrcGroup i && i.controlsSource(fo)) {
        return new SourceLevelQueryImplementation2.Result() {
          @Override
          public String getSourceLevel() {
            return i.source;
          }

          @Override
          public void addChangeListener(ChangeListener cl) {
          }

          @Override
          public void removeChangeListener(ChangeListener cl) {
          }
        };
      }
    }
    return null;
  }

  @Override
  public CompilerOptionsQueryImplementation.Result getOptions(FileObject fo) {
    for (var g : sources) {
      if (g instanceof SrcGroup i && i.controlsSource(fo)) {
        return new CompilerOptionsQueryImplementation.Result() {
          @Override
          public List<? extends String> getArguments() {
            return i.options;
          }

          @Override
          public void addChangeListener(ChangeListener cl) {
          }

          @Override
          public void removeChangeListener(ChangeListener cl) {
          }
        };
      }
    }
    return null;
  }

  @Override
  public SourceGroup[] getSourceGroups(String type) {
    if (Sources.TYPE_GENERIC.equals(type)) {
      var dir = project.getProjectDirectory();
      var displayname = FileUtil.getFileDisplayName(dir);
      var icon = ImageUtilities.loadImageIcon("org/enso/tools/enso4igv/enso-duke.svg", true);
      var genericGroup = GenericSources.group(project, dir.getFileObject("src", false), dir.getNameExt(), displayname, icon, icon);
      return new SourceGroup[]{genericGroup};
    }
    return sources;
  }

  @Override
  public void addChangeListener(ChangeListener cl) {
  }

  @Override
  public void removeChangeListener(ChangeListener cl) {
  }

  @Override
  public SrcGroup findBinaryRoots2(URL url) {
    var fo = URLMapper.findFileObject(url);
    for (var g : sources) {
      if (g instanceof SrcGroup i && (i.outputsTo(fo) || i.controlsSource(fo))) {
        return i;
      }
    }
    return null;
  }

  @Override
  public URL[] computeRoots(SrcGroup result) {
    if (result.output != null) {
      return new URL[]{result.output.toURL()};
    } else {
      return new URL[0];
    }
  }

  @Override
  public boolean computePreferBinaries(SrcGroup result) {
    return true;
  }

  @Override
  public void computeChangeListener(SrcGroup result, boolean bln, ChangeListener cl) {
  }

  @Override
  public SourceForBinaryQueryImplementation2.Result findSourceRoots2(URL url) {
    var fo = URLMapper.findFileObject(url);
    if (fo == null) {
      return null;
    }
    for (var g : sources) {
      if (g instanceof SrcGroup i && (i.outputsTo(fo) || i.controlsSource(fo))) {
        return new SourceForBinaryQueryImplementation2.Result() {
          @Override
          public boolean preferSources() {
            return false;
          }

          @Override
          public FileObject[] getRoots() {
            return i.getRoots();
          }

          @Override
          public void addChangeListener(ChangeListener l) {
          }

          @Override
          public void removeChangeListener(ChangeListener l) {
          }
        };
      }
    }
    return null;
  }

  @Override
  public SourceForBinaryQuery.Result findSourceRoots(URL binaryRoot) {
    return findSourceRoots2(binaryRoot);
  }

  static final class SrcGroup implements SourceGroup {
    private final ClassPath cp;
    private final ClassPath moduleCp;
    private final ClassPath srcCp;
    private final JavaPlatform platform;
    private final FileObject output;
    private final String source;
    private final List<String> options;

    private SrcGroup(
      ClassPath cp,
      ClassPath moduleCp,
      ClassPath srcCp,
      JavaPlatform platform,
      FileObject output,
      String source,
      List<String> options
    ) {
      this.cp = cp;
      this.moduleCp = moduleCp;
      this.srcCp = srcCp;
      this.platform = platform;
      this.output = output;
      this.source = source;
      this.options = options;
    }

    @Override
    public FileObject getRootFolder() {
      var arr = srcCp.getRoots();
      if (arr.length == 0) {
        LOG.log(Level.SEVERE, "Source classpath is empty for {0}", this);
        return output;
      }
      return arr[0];
    }

    private FileObject[] getRoots() {
      return srcCp.getRoots();
    }

    @Override
    public String getName() {
      return getRootFolder().getNameExt();
    }

    @Override
    public String getDisplayName() {
      return "Java " + source + " " + getName();
    }

    @Override
    public Icon getIcon(boolean bln) {
      return null;
    }

    @Override
    public boolean contains(FileObject fo) {
      if (getRootFolder().equals(fo)) {
        return true;
      }
      return FileUtil.isParentOf(getRootFolder(), fo);
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener pl) {
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener pl) {
    }

    private boolean controlsSource(FileObject fo) {
      return contains(fo) || srcCp.contains(fo);
    }

    private boolean outputsTo(FileObject fo) {
      if (fo == null || output == null) {
        return false;
      }
      if (fo.equals(output)) {
        return true;
      }
      return FileUtil.isParentOf(output, fo);
    }

    public String toString() {
      return "EnsoSources[name=" + getName() + ",root=" + getRootFolder() + ",output=" + output + "]";
    }
  }

  record OtherEnsoSources(String kind, FileObject root) implements SourceGroup {
    @Override
    public FileObject getRootFolder() {
      return root;
    }

    @Override
    public String getName() {
      return kind + "/" + root.getNameExt();
    }

    @Override
    public String getDisplayName() {
      return getName();
    }

    @Override
    public Icon getIcon(boolean bln) {
      return null;
    }

    @Override
    public boolean contains(FileObject fo) {
      return FileUtil.isParentOf(root, fo);
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener pl) {
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener pl) {
    }
  }
}
