package il.lamerhav.dsviz;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.ui.handlers.HandlerUtil;

/** "הסברים בעברית לשגיאות" בתפריט מבני נתונים: מפעיל / מכבה את המורה בעברית, ובונה מחדש כדי שההערות יופיעו או ייעלמו */
public class MentorToggleHandler extends AbstractHandler {
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        boolean on = !HandlerUtil.toggleCommandState(event.getCommand());
        MentorParticipant.setEnabled(on);
        WorkspaceJob job = new WorkspaceJob(on ? "מבני נתונים — מוסיף הסברים בעברית" : "מבני נתונים — מסיר את ההסברים בעברית") {
            @Override
            public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
                for (IProject p : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
                    if (p.isOpen() && p.hasNature(JavaCore.NATURE_ID)) p.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
                }
                return Status.OK_STATUS;
            }
        };
        job.setRule(ResourcesPlugin.getWorkspace().getRuleFactory().buildRule());
        job.schedule();
        return null;
    }
}
