package org.eclipse.team.internal.ccvs.core.resources;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.team.ccvs.core.ICVSRemoteFolder;
import org.eclipse.team.ccvs.core.ICVSRemoteResource;
import org.eclipse.team.ccvs.core.ICVSRepositoryLocation;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.sync.IRemoteResource;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.Client;
import org.eclipse.team.internal.ccvs.core.Policy;
import org.eclipse.team.internal.ccvs.core.connection.CVSRepositoryLocation;
import org.eclipse.team.internal.ccvs.core.connection.CVSServerException;
import org.eclipse.team.internal.ccvs.core.connection.Connection;
import org.eclipse.team.internal.ccvs.core.resources.api.FolderProperties;
import org.eclipse.team.internal.ccvs.core.resources.api.IManagedFile;
import org.eclipse.team.internal.ccvs.core.resources.api.IManagedFolder;
import org.eclipse.team.internal.ccvs.core.resources.api.IManagedResource;
import org.eclipse.team.internal.ccvs.core.resources.api.IManagedVisitor;
import org.eclipse.team.internal.ccvs.core.response.IResponseHandler;
import org.eclipse.team.internal.ccvs.core.response.custom.IStatusListener;
import org.eclipse.team.internal.ccvs.core.response.custom.IUpdateMessageListener;
import org.eclipse.team.internal.ccvs.core.response.custom.StatusErrorHandler;
import org.eclipse.team.internal.ccvs.core.response.custom.StatusMessageHandler;
import org.eclipse.team.internal.ccvs.core.response.custom.UpdateErrorHandler;
import org.eclipse.team.internal.ccvs.core.response.custom.UpdateMessageHandler;

/**
 * This class provides the implementation of ICVSRemoteFolder
 */
public class RemoteFolder extends RemoteResource implements ICVSRemoteFolder, IManagedFolder {

	private ICVSRemoteResource[] children;
	private CVSRepositoryLocation repository;
	private IPath repositoryRelativePath;
	
	/**
	 * Constructor for RemoteFolder.
	 */
	public RemoteFolder(ICVSRepositoryLocation repository, IPath repositoryRelativePath, String tag) {
		super(repositoryRelativePath.lastSegment(), tag);
		this.repository = (CVSRepositoryLocation)repository;
		this.repositoryRelativePath = repositoryRelativePath;
	}

	// Get the file revisions for the given filenames
	protected void updateFileRevisions(Connection connection, String[] fileNames, IProgressMonitor monitor) throws CVSException {
		
		final int[] count = new int[] {0};
		
		// Create a listener for receiving the revision info
		final Map revisions = new HashMap();
		IStatusListener listener = new IStatusListener() {
			public void fileStatus(IPath path, String remoteRevision) {
				if (remoteRevision == IStatusListener.FOLDER_RIVISION)
					// Ignore any folders
					return;
				try {
					((RemoteFile)getChild(path.lastSegment())).setRevision(remoteRevision);
					count[0]++;
				} catch (CVSException e) {
					// The count wil be off to indicate an error
				}
			}
		};
			
		// Perform a "cvs status..." with a custom message handler
		final List errors = new ArrayList();
		try {
			Client.execute(
				Client.STATUS,
				Client.EMPTY_ARGS_LIST,
				Client.EMPTY_ARGS_LIST,
				fileNames,
				this,
				monitor,
				getPrintStream(),
				connection,
				new IResponseHandler[] {new StatusMessageHandler(listener),new StatusErrorHandler(listener, errors)},
				false);
		} catch (CVSException e) {
			if (!errors.isEmpty()) {
				PrintStream out = getPrintStream();
				for (int i=0;i<errors.size();i++)
					out.println(errors.get(i));
			}
			throw e;
		}
		
		if (count[0] != fileNames.length)
			throw new CVSException(Policy.bind("RemoteFolder.errorFetchingRevisions"));
	}
	
	 /**
	 * @see IManagedResource#accept(IManagedVisitor)
	 */
	public void accept(IManagedVisitor visitor) throws CVSException {
		visitor.visitFolder(this);
	}

	/**
	 * @see ICVSRemoteFolder#getMembers()
	 */
	public ICVSRemoteResource[] getMembers(IProgressMonitor monitor) throws TeamException {
		return getMembers(tag, monitor);
	}

	/*
	 * This method gets the members for a given tag and returns them.
	 * During the execution of this method, the instance variable children
	 * will be used to contain the children. However, the variable is reset
	 * and the result returned. Thus, instances of RemoteFolder do not
	 * persist the children. Subclasses (namely RemoteFolderTree) may
	 * persist the children.
	 */
	public ICVSRemoteResource[] getMembers(final String tagName, IProgressMonitor monitor) throws TeamException {
		
		final IProgressMonitor progress = Policy.monitorFor(monitor);
		
		// Forget about our children
		children = null;
		
		// Create the listener for remote files and folders
		final List errors = new ArrayList();
		final List newRemoteDirectories = new ArrayList();
		final List newRemoteFiles = new ArrayList();
		IUpdateMessageListener listener = new IUpdateMessageListener() {
			public void directoryInformation(IPath path, boolean newDirectory) {
				if (newDirectory && path.segmentCount() == 1) {
					newRemoteDirectories.add(path.lastSegment());
					progress.subTask(path.lastSegment().toString());
					progress.worked(1);
				}
			}
			public void directoryDoesNotExist(IPath path) {
			}
			public void fileInformation(char type, String filename) {
				IPath filePath = new Path(filename);	
				if( filePath.segmentCount() == 1 ) {
					String properFilename = filePath.lastSegment();
					newRemoteFiles.add(properFilename);
					progress.subTask(properFilename);
					progress.worked(1);
				}
			}
		};
		
		// Build the local options
		List localOptions = new ArrayList();
		localOptions.add("-d");
		if ((tagName != null) && (!tagName.equals("HEAD"))) {
			localOptions.add(Client.TAG_OPTION);
			localOptions.add(tagName);
		}
		
		// Retrieve the children and any file revision numbers in a single connection
		Connection c = ((CVSRepositoryLocation)getRepository()).openConnection();
		try {
			// Perform a "cvs -n update -d -r tagName folderName" with custom message and error handlers
			Client.execute(
				Client.UPDATE,
				new String[]{Client.NOCHANGE_OPTION}, 
				(String[])localOptions.toArray(new String[localOptions.size()]), 
				new String[]{"."}, 
				this,
				monitor,
				getPrintStream(),
				c,
				new IResponseHandler[]{new UpdateMessageHandler(listener), new UpdateErrorHandler(listener, errors)},
				true);

			if (progress.isCanceled()) {
				throw new OperationCanceledException();
			}

			// Convert the file and folder names to IManagedResources
			List result = new ArrayList();
			for (int i=0;i<newRemoteFiles.size();i++) {
				result.add(new RemoteFile(this, (String)newRemoteFiles.get(i), null));
			}
			for (int i=0;i<newRemoteDirectories.size();i++)
				result.add(new RemoteFolder(getRepository(), repositoryRelativePath.append((String)newRemoteDirectories.get(i)), tagName));
			children = (ICVSRemoteResource[])result.toArray(new ICVSRemoteResource[0]);

			// Get the revision numbers for the files
			if (newRemoteFiles.size() > 0) {
				updateFileRevisions(c, (String[])newRemoteFiles.toArray(new String[newRemoteFiles.size()]), monitor);
			}
			
		} catch (CVSServerException e) {
			if( ! isNoTagException(errors) )
				throw e;
				// we now know that this is an exception caused by a cvs bug.
				// if the folder has no files in it (just subfolders) cvs does not respond with the subfolders...
				// workaround: retry the request with no tag to get the directory names (if any)
			Policy.checkCanceled(progress);
			children = getMembers(null, progress);
 		} catch (CVSException e) {
			if (!errors.isEmpty()) {
				PrintStream out = getPrintStream();
				for (int i=0;i<errors.size();i++)
					out.println(errors.get(i));
			}
			throw e;
		} finally {
			c.close();
		}

		// Forget the children
		ICVSRemoteResource[] result = children;
		children = null;
		return result;
	}

	/**
	 * @see IManagedFolder#getFolders()
	 */
	public IManagedFolder[] getFolders() throws CVSException {
		ICVSRemoteResource[] children = getChildren();
		if (children == null)
			return new IManagedFolder[0];
		else {
			List result = new ArrayList();
			for (int i=0;i<children.length;i++)
				if (((IManagedResource)children[i]).isFolder())
					result.add(children[i]);
			return (IManagedFolder[])result.toArray(new IManagedFolder[result.size()]);
		}
	}

	/**
	 * @see IManagedFolder#getFiles()
	 */
	public IManagedFile[] getFiles() throws CVSException {
		ICVSRemoteResource[] children = getChildren();
		if (children == null)
			return new IManagedFile[0];
		else {
			List result = new ArrayList();
			for (int i=0;i<children.length;i++)
				if (!((IManagedResource)children[i]).isFolder())
					result.add(children[i]);
			return (IManagedFile[])result.toArray(new IManagedFile[result.size()]);
		}
	}

	/**
	 * @see IManagedFolder#getFolder(String)
	 */
	public IManagedFolder getFolder(String name) throws CVSException {
		if (name.equals(Client.CURRENT_LOCAL_FOLDER) || name.equals(Client.CURRENT_LOCAL_FOLDER + Client.SERVER_SEPARATOR))
			return this;
		IManagedResource child = getChild(name);
		if (child.isFolder())
			return (IManagedFolder)child;
		throw new CVSException(Policy.bind("RemoteFolder.invalidChild", new Object[] {name}));
	}

	/**
	 * @see IManagedFolder#getFile(String)
	 */
	public IManagedFile getFile(String name) throws CVSException {
		IManagedResource child = getChild(name);
		if (!child.isFolder())
			return (IManagedFile)child;
		throw new CVSException(Policy.bind("RemoteFolder.invalidChild", new Object[] {name}));

	}

	public String getRemotePath() {
		return repositoryRelativePath.toString();
	}
	
	/**
	 * @see IManagedResource#getRelativePath(IManagedFolder)
	 */
	public String getRelativePath(IManagedFolder ancestor) throws CVSException {
		if (ancestor == this)
			return ".";
		// NOTE: This is a quick and dirty way.
		return this.getRemotePath().substring(((RemoteFolder)ancestor).getRemotePath().length() + 1);
		// throw new CVSException(Policy.bind("RemoteFolder.invalidOperation"));
	}
	
	public ICVSRepositoryLocation getRepository() {
		return repository;
	}
	
	/**
	 * @see IManagedResource#isFolder()
	 */
	public boolean isFolder() {
		return true;
	}
	
	/*
	 * Return true if the exception from the cvs server is the no tag error, and false
	 * otherwise.
	 */
	protected boolean isNoTagException(List errors) {
		if (errors.size() != 1)
			return false;
		if (((IStatus)errors.get(0)).getMessage().startsWith("cvs [server aborted]: no such tag"))
			return true;
		return false;
	}

	/**
	 * @see IManagedFolder#childExists(String)
	 */
	public boolean childExists(String path) {
		try {
			return getChild(path) != null;
		} catch (CVSException e) {
			return false;
		}
	}

	/**
	 * @see IManagedFolder#getChild(String)
	 * 
	 * XXX: shouldn't this consider the case where children is null. Maybe
	 * by running the update + status with only one member?
	 */
	public IManagedResource getChild(String path) throws CVSException {
		ICVSRemoteResource[] children = getChildren();
		if (path.equals(Client.CURRENT_LOCAL_FOLDER) || children == null)
			return this;
		// NOTE: We only search down one level for now!!!
		if (path.indexOf(Client.SERVER_SEPARATOR) == -1) {
			for (int i=0;i<children.length;i++) {
				if (children[i].getName().equals(path))
					return (IManagedResource)children[i];
			}
		} else {
			IPath p = new Path(path);
			return ((IManagedFolder)getChild(p.segment(0))).getChild(p.removeFirstSegments(1).toString());
		}
		throw new CVSException(Policy.bind("RemoteFolder.invalidChild", new Object[] {name}));
	}

	/**
	 * @see IManagedFolder#mkdir()
	 */
	public void mkdir() throws CVSException {
		throw new CVSException(Policy.bind("RemoteResource.invalidOperation"));
	}

	/**
	 * @see IManagedFolder#flush(boolean)
	 */
	public void flush(boolean deep) {
	}

	/**
	 * @see IManagedFolder#getFolderInfo()
	 */
	public FolderProperties getFolderInfo() throws CVSException {
		FolderProperties fp = new FolderProperties(getRepository().getLocation(), getRemotePath(), false);
		if ((tag != null) && !(tag.equals("HEAD")))
			fp.setTag("T" + tag);
		return fp;
	}

	/**
	 * @see IManagedFolder#setFolderInfo(FolderProperties)
	 */
	public void setFolderInfo(FolderProperties folderInfo) throws CVSException {
	}

	/**
	 * @see IManagedFolder#setProperty(String, String[])
	 */
	public void setProperty(String key, String[] content) throws CVSException {
	}

	/**
	 * @see IManagedFolder#unsetProperty(String)
	 */
	public void unsetProperty(String key) throws CVSException {
	}

	/**
	 * @see IManagedFolder#getProperty(String)
	 */
	public String[] getProperty(String key) throws CVSException {
		throw new CVSException(Policy.bind("RemoteResource.invalidOperation"));
	}

	/**
	 * @see IManagedResource#getRemoteLocation(IManagedFolder)
	 */
	public String getRemoteLocation(IManagedFolder stopSearching) throws CVSException {
		return getRepository().getRootDirectory() + Client.SERVER_SEPARATOR + getRemotePath();
	}
	
	/**
	 * @see IManagedFolder#isCVSFolder()
	 */
	public boolean isCVSFolder() throws CVSException {
		return true;
	}

	/**
	 * @see IManagedFolder#acceptChildren(IManagedVisitor)
	 */
	public void acceptChildren(IManagedVisitor visitor) throws CVSException {
		throw new CVSException(Policy.bind("RemoteResource.invalidOperation"));
	}
	
	/*
	 * @see IRemoteResource#isContainer()
	 */
	public boolean isContainer() {
		return true;
	}
	
	/*
	 * @see IRemoteResource#members(IProgressMonitor)
	 */
	public IRemoteResource[] members(IProgressMonitor progress) throws TeamException {
		return getMembers(progress);
	}

	/*
	 * @see IRemoteResource#getContents(IProgressMonitor)
	 */
	public InputStream getContents(IProgressMonitor progress) throws TeamException {
		return null;
	}

	/*
	 * Answers the immediate cached children of this remote folder or null if the remote folder
	 * handle has not yet queried the server for the its children.
	 */	
	protected ICVSRemoteResource[] getChildren() {
		return children;
	}
	
	/*
	 * This allows subclass to set the children
	 */
	protected void setChildren(ICVSRemoteResource[] children) {
		this.children = children;
	}
	/*
	 * @see ICVSRemoteFolder#setTag(String)
	 */
	public void setTag(String tagName) {
		tag = tagName;
	}

	/*
	 * @see ICVSRemoteFolder#getTag()
	 */
	public String getTag() {
		return tag;
	}
}