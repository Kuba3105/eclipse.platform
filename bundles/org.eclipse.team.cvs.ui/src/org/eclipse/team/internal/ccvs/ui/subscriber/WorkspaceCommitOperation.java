/*******************************************************************************
 * Copyright (c) 2000, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.internal.ccvs.ui.subscriber;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.compare.structuremergeviewer.IDiffElement;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.mapping.ResourceMappingContext;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.mapping.provider.SynchronizationScopeManager;
import org.eclipse.team.core.synchronize.SyncInfo;
import org.eclipse.team.core.synchronize.SyncInfoSet;
import org.eclipse.team.internal.ccvs.core.*;
import org.eclipse.team.internal.ccvs.core.client.Command;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.ui.*;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.eclipse.team.internal.ccvs.ui.operations.*;
import org.eclipse.team.internal.ccvs.ui.repo.RepositoryManager;
import org.eclipse.team.internal.core.subscribers.ChangeSet;
import org.eclipse.team.internal.ui.Utils;
import org.eclipse.team.ui.synchronize.ISynchronizePageConfiguration;

public class WorkspaceCommitOperation extends CVSSubscriberOperation {
	
	private String comment;
	private SyncInfoSet syncSet;
	private boolean override;

	public WorkspaceCommitOperation(ISynchronizePageConfiguration configuration, IDiffElement[] elements, boolean override) {
		super(configuration, elements);
		this.override = override;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.subscriber.CVSSubscriberOperation#getErrorTitle()
	 */
	protected String getErrorTitle() {
		return CVSUIMessages.CommitAction_commitFailed; 
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ui.actions.TeamOperation#getJobName()
	 */
	protected String getJobName() {
		SyncInfoSet syncSet = getSyncInfoSet();
		return NLS.bind(CVSUIMessages.CommitAction_jobName, new String[] { new Integer(syncSet.size()).toString() }); 
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.TeamOperation#shouldRun()
	 */
	public boolean shouldRun() {
		SyncInfoSet set = getSyncInfoSet();
		return !set.isEmpty();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ui.actions.SubscriberOperation#getSyncInfoSet()
	 */
	protected SyncInfoSet getSyncInfoSet() {
		if (syncSet == null) {
			syncSet = super.getSyncInfoSet();
			if (!promptForConflictHandling(syncSet)) {
				syncSet.clear();
				return syncSet;
			}
			try {
				if (!promptForUnaddedHandling(syncSet)) {
					syncSet.clear();
					return syncSet;
				}
			} catch (CVSException e) {
				Utils.handle(e);
				syncSet.clear();
			}
		}
		return syncSet;
	}
	
	protected boolean promptForConflictHandling(SyncInfoSet syncSet) {
		if (syncSet.hasConflicts() || syncSet.hasIncomingChanges()) {
			if (override) {
				// If overriding, prompt to ensure that is what the user wants
				switch (promptForConflicts(syncSet)) {
					case 0:
						// Yes, synchronize conflicts as well
						break;
					case 1:
						// No, stop here
						return false;
					case 2:
					default:
						// Cancel
						return false;
				}	
			} else {
				// If there is a conflict in the syncSet, remove from sync set.
				syncSet.removeConflictingNodes();
				syncSet.removeIncomingNodes();
			}
		}
		return true;
	}
	
	/*
	 *  (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.subscriber.CVSSubscriberOperation#run(org.eclipse.team.core.synchronize.SyncInfoSet, org.eclipse.core.runtime.IProgressMonitor)
	 */
	public void runWithProjectRule(IProject project, SyncInfoSet syncSet, IProgressMonitor monitor) throws TeamException {
		
		final SyncInfo[] changed = syncSet.getSyncInfos();
		if (changed.length == 0) return;
		
		// A list of files to be committed
		final List commits = new ArrayList(); // of IResource
		// New resources that are not yet under CVS control and need a "cvs add"
		final List additions = new ArrayList(); // of IResource
		// A list of incoming or conflicting file changes to be made outgoing changes
		final List makeOutgoing = new ArrayList(); // of SyncInfo
		// A list of out-of-sync folders that must be made in-sync
		final List makeInSync = new ArrayList(); // of SyncInfo
		
		for (int i = 0; i < changed.length; i++) {
			SyncInfo changedNode = changed[i];
			int kind = changedNode.getKind();
			IResource resource = changedNode.getLocal();
			
			// Any parent folders should be made in-sync.
			// Steps will be taken after the commit to prune any empty folders
			SyncInfo parent = getParent(changedNode);
			if (parent != null) {
				if (isOutOfSync(parent)) {
					makeInSync.add(parent);
				}
			}
			
			if (resource.getType() == IResource.FILE) {
				// By default, all files are committed
				commits.add(resource);
				// Determine what other work needs to be done for the file
				switch (kind & SyncInfo.DIRECTION_MASK) {
					case SyncInfo.INCOMING:
						// Convert the incoming change to an outgoing change
						makeOutgoing.add(changedNode);
						break;
					case SyncInfo.OUTGOING:
						switch (kind & SyncInfo.CHANGE_MASK) {
							case SyncInfo.ADDITION:
								// Outgoing addition. 'add' it before committing.
								if (!isAdded(resource))
									additions.add(resource);
								break;
							case SyncInfo.DELETION:
								// Outgoing deletion is handled by move/delete
								// hook and EclipseSynchronizer
								break;
							case SyncInfo.CHANGE:
								// Outgoing change. Just commit it.
								break;
						}
						break;
					case SyncInfo.CONFLICTING:
						// Convert the conflicting change to an outgoing change
						makeOutgoing.add(changedNode);
						break;
				}
			} else {
				if (((kind & SyncInfo.DIRECTION_MASK) == SyncInfo.OUTGOING)
					&& ((kind & SyncInfo.CHANGE_MASK) == SyncInfo.ADDITION)) {
						// Outgoing folder additions must be added
						additions.add(changedNode.getLocal());
				} else if (isOutOfSync(changedNode)) {
					// otherwise, make any out-of-sync folders in-sync using the remote info
					makeInSync.add(changedNode);
				}
			}
		}
		monitor.beginTask(null, 200);
		
		if (makeInSync.size() > 0) {
			makeInSync((SyncInfo[]) makeInSync.toArray(new SyncInfo[makeInSync.size()]), Policy.subMonitorFor(monitor, 25));			
		}

		if (makeOutgoing.size() > 0) {
			makeOutgoing((SyncInfo[]) makeOutgoing.toArray(new SyncInfo[makeInSync.size()]), Policy.subMonitorFor(monitor, 25));			
		}

		if (additions.size() != 0) {
			add(project, (IResource[])additions.toArray(new IResource[0]), Policy.subMonitorFor(monitor, 50));
		}
		commit(project, (IResource[])commits.toArray(new IResource[commits.size()]), Policy.subMonitorFor(monitor, 100));		
	}	
	
	private void commit(final IProject project, IResource[] commits, IProgressMonitor monitor) throws TeamException {
		try {
			CommitOperation commitOperation = new CommitOperation(getPart(), RepositoryProviderOperation.asResourceMappers(commits),
					new Command.LocalOption[0], comment) {
				protected ResourceMappingContext getResourceMappingContext() {
					return new SingleProjectSubscriberContext(CVSProviderPlugin.getPlugin().getCVSWorkspaceSubscriber(), false, project);
				}
				protected SynchronizationScopeManager createScopeManager(boolean consultModels) {
					return new SingleProjectScopeManager(getJobName(), getSelectedMappings(), getResourceMappingContext(), consultModels, project);
				}
			};
			commitOperation
						.run(monitor);
		} catch (InvocationTargetException e) {
			throw TeamException.asTeamException(e);
		} catch (InterruptedException e) {
			throw new OperationCanceledException();
		}
	}

	private void add(final IProject project, IResource[] additions, IProgressMonitor monitor) throws TeamException {
		try {
			new AddOperation(getPart(), RepositoryProviderOperation.asResourceMappers(additions)) {
				protected ResourceMappingContext getResourceMappingContext() {
					return new SingleProjectSubscriberContext(CVSProviderPlugin.getPlugin().getCVSWorkspaceSubscriber(), false, project);
				}
				protected SynchronizationScopeManager createScopeManager(boolean consultModels) {
					return new SingleProjectScopeManager(getJobName(), getSelectedMappings(), getResourceMappingContext(), consultModels, project);
				}
			}.run(monitor);
		} catch (InvocationTargetException e1) {
			throw TeamException.asTeamException(e1);
		} catch (InterruptedException e1) {
			throw new OperationCanceledException();
		}
	}

	/**
	 * Prompts the user to determine how conflicting changes should be handled.
	 * Note: This method is designed to be overridden by test cases.
	 * @return 0 to sync conflicts, 1 to sync all non-conflicts, 2 to cancel
	 */
	protected int promptForConflicts(SyncInfoSet syncSet) {
		String[] buttons = new String[] {IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL, IDialogConstants.CANCEL_LABEL};
		String question = CVSUIMessages.CommitSyncAction_questionRelease; 
		String title = CVSUIMessages.CommitSyncAction_titleRelease; 
		String[] tips = new String[] {
			CVSUIMessages.CommitSyncAction_releaseAll, 
			CVSUIMessages.CommitSyncAction_releasePart, 
			CVSUIMessages.CommitSyncAction_cancelRelease
		};
		Shell shell = getShell();
		final ToolTipMessageDialog dialog = new ToolTipMessageDialog(shell, title, null, question, MessageDialog.QUESTION, buttons, tips, 0);
		shell.getDisplay().syncExec(new Runnable() {
			public void run() {
				dialog.open();
			}
		});
		return dialog.getReturnCode();
	}
		
	/**
	 * Prompts the user for a release comment.
	 * Note: This method is designed to be overridden by test cases.
	 * @return the comment, or null to cancel
	 */
	protected String promptForComment(RepositoryManager manager, IResource[] resourcesToCommit) {
	    String proposedComment = getProposedComment(resourcesToCommit);
		return manager.promptForComment(getShell(), resourcesToCommit, proposedComment);
	}

    private String getProposedComment(IResource[] resourcesToCommit) {
    	StringBuffer comment = new StringBuffer();
        ChangeSet[] sets = CVSUIPlugin.getPlugin().getChangeSetManager().getSortedSets();
        int numMatchedSets = 0;
        for (int i = 0; i < sets.length; i++) {
            ChangeSet set = sets[i];
            if (containsOne(set, resourcesToCommit)) {
            	if(numMatchedSets > 0) comment.append(System.getProperty("line.separator")); //$NON-NLS-1$
                comment.append(set.getComment());
                numMatchedSets++;
            }
        }
        return comment.toString();
    }
    
    private boolean containsOne(ChangeSet set, IResource[] resourcesToCommit) {
    	 for (int j = 0; j < resourcesToCommit.length; j++) {
            IResource resource = resourcesToCommit[j];
            if (set.contains(resource)) {
                return true;
            }
        }
        return false;
    }

    protected IResource[] promptForResourcesToBeAdded(RepositoryManager manager, IResource[] unadded) {
		return manager.promptForResourcesToBeAdded(getShell(), unadded);
	}
	
	private boolean promptForUnaddedHandling(SyncInfoSet syncSet) throws CVSException {
		if (syncSet.isEmpty()) return false;
		
		// accumulate any resources that are not under version control
		IResource[] unadded = getUnaddedResources(syncSet);
		
		// prompt to get comment and any resources to be added to version control
		RepositoryManager manager = CVSUIPlugin.getPlugin().getRepositoryManager();
		IResource[] toBeAdded = promptForResourcesToBeAdded(manager, unadded);
		if (toBeAdded == null) return false; // User cancelled.
		comment = promptForComment(manager, syncSet.getResources());
		if (comment == null) return false; // User cancelled.
		
		// remove unshared resources that were not selected by the user
		if (unadded != null && unadded.length > 0) {
			List resourcesToRemove = new ArrayList(unadded.length);
			for (int i = 0; i < unadded.length; i++) {
				IResource unaddedResource = unadded[i];
				boolean included = false;
				for (int j = 0; j < toBeAdded.length; j++) {
					IResource resourceToAdd = toBeAdded[j];
					if (unaddedResource.equals(resourceToAdd)) {
						included = true;
						break;
					}
				}
				if (!included)
					resourcesToRemove.add(unaddedResource);
			}
			syncSet.removeAll((IResource[]) resourcesToRemove.toArray(new IResource[resourcesToRemove.size()]));
		}
		return true;
	}
	
	private IResource[] getUnaddedResources(SyncInfoSet syncSet) throws CVSException {
		// TODO: Should only get outgoing additions (since conflicting additions 
		// could be considered to be under version control already)
		IResource[] resources = syncSet.getResources();
		List result = new ArrayList();
		for (int i = 0; i < resources.length; i++) {
			IResource resource = resources[i];
			if (!isAdded(resource)) {
				result.add(resource);
			}
		}
		return (IResource[]) result.toArray(new IResource[result.size()]);
	}

	private boolean isAdded(IResource resource) throws CVSException {
		ICVSResource cvsResource = CVSWorkspaceRoot.getCVSResourceFor(resource);
		if (cvsResource.isFolder()) {
			return ((ICVSFolder)cvsResource).isCVSFolder();
		} else {
			return cvsResource.isManaged();
		}
	}

}
