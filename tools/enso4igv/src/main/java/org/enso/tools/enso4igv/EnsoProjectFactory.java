package org.enso.tools.enso4igv;

import java.awt.Image;
import java.io.IOException;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.spi.project.ProjectFactory;
import org.netbeans.spi.project.ProjectFactory2;
import org.netbeans.spi.project.ProjectState;
import org.openide.filesystems.FileObject;
import org.openide.util.ImageUtilities;
import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service = ProjectFactory.class, position = 135)
public final class EnsoProjectFactory implements ProjectFactory2 {
  static int isProjectCheck(FileObject fo) {
    var yaml = fo.getFileObject("elide.pkl");
    if (yaml != null) {
        return 1;
    }
    return 0;
  }

  private static Project createProjectOrNull(FileObject fo, ProjectState ps) throws IOException {
    return switch (isProjectCheck(fo)) {
      case 1 -> new ElideProject(fo, ps);
      default -> null;
    };
  }

  @Override
  public boolean isProject(FileObject fo) {
    return isProjectCheck(fo) != 0;
  }

  @Override
  public Project loadProject(FileObject fo, ProjectState ps) throws IOException {
    return createProjectOrNull(fo, ps);
  }


  public void saveProject(Project prjct) throws IOException, ClassCastException {
  }

  @Override
  public ProjectManager.Result isProject2(FileObject fo) {
    var img = findImageForType(fo);
    return img == null ? null : new ProjectManager.Result(ImageUtilities.image2Icon(img));
  }

    private static Image findImageForType(FileObject fo) {
        var img = switch (isProjectCheck(fo)) {
            case 1 -> ImageUtilities.loadImage("org/enso/tools/enso4igv/elidelogo.svg");
            default -> null;
        };  return img;
    }

}
