package org.eclipse.team.internal.ccvs.core.filesystem;

import java.util.HashMap;

import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.provider.FileInfo;
import org.eclipse.core.runtime.*;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ccvs.core.*;
import org.eclipse.team.internal.ccvs.core.resources.RemoteFolderTree;

public class CVSFileTree {

	private RemoteFolderTree remoteTree;
	//HashMap of RemoteFolders used to speed up look up
	private HashMap remoteFolders;
	private HashMap logMap;
	private CVSURI baseURI;
	
	public CVSFileTree(IFileStore treeRoot, CVSURI uri, RemoteFolderTree remoteTree, HashMap remoteFolders, HashMap logMap) {
		this.remoteTree = remoteTree;
		this.baseURI = uri;
		this.remoteFolders = remoteFolders;
		this.logMap = logMap;
	}

	public IFileStore[] getChildrenFileStores(IFileStore store) {
		CVSURI cvsUri = CVSURI.fromUri(store.toURI());
		RemoteFolderTree folderTree = (RemoteFolderTree) remoteFolders.get(cvsUri.getProjectStrippedPath().toString());
		
		if (folderTree == null)
			return null;
		
		ICVSRemoteResource[] remoteResources = folderTree.getChildren();
		IFileStore[] fileStore = new IFileStore[remoteResources.length];
		for (int i = 0; i < remoteResources.length; i++) {
			IFileInfo fileInfo;
			try {
				fileInfo = getFileInfo((ICVSResource) remoteResources[i], new NullProgressMonitor());
				fileStore[i] = new CVSFileStore(baseURI.append(fileInfo.getName()), fileInfo);
			} catch (TeamException e) {}
		}
		return fileStore;
	}

	private IFileInfo getFileInfo(ICVSResource resource, IProgressMonitor monitor) throws TeamException {
		monitor = Policy.monitorFor(monitor);
		if (resource == null)
			return null;
		FileInfo info = new FileInfo();
		info.setExists(true);
		info.setName(resource.getName());
		if (!resource.isFolder()) {
			ICVSRemoteFile file = (ICVSRemoteFile) resource;
			//TODO: how to handle entries that are not found
			ILogEntry entry = (ILogEntry) logMap.get(file);
			//ILogEntry entry = file.getLogEntry(monitor);
			info.setLastModified(entry.getDate().getTime());
		} else {
			info.setLastModified(0);
			info.setDirectory(true);
		}
		return info;
	}

	public IFileInfo[] getChildrenFileInfos(IFileStore store) {
	
		CVSURI cvsUri = CVSURI.fromUri(store.toURI());
		RemoteFolderTree folderTree = (RemoteFolderTree) remoteFolders.get(cvsUri.getProjectStrippedPath().toString());
		
		if (folderTree == null)
			return null;
		
		ICVSRemoteResource[] remoteResources = folderTree.getChildren();
		IFileInfo[] fileInfos = new IFileInfo[remoteResources.length];
		for (int i = 0; i < remoteResources.length; i++) {
			IFileInfo fileInfo;
			try {
				fileInfo = getFileInfo((ICVSResource) remoteResources[i], new NullProgressMonitor());
				fileInfos[i] = fileInfo;
			} catch (TeamException e) {}
		}
		return fileInfos;
	}

	public IFileInfo getFileInfo(IFileStore store) {
		ICVSRemoteFolder folder=null;
		String repoName = null, resourceName = null;
		CVSURI cvsUri = CVSURI.fromUri(store.toURI());
		
	
			
		repoName = cvsUri.getRepositoryName();
		folder = cvsUri.getParentFolder();
		resourceName = cvsUri.getLastSegment();
		
		if (folder.getName().equals("")) {
			// this is the repo root so return an info that indicates this
			FileInfo info = new FileInfo();
			info.setExists(true);
			info.setName(resourceName);
			info.setDirectory(true);
			return info;
		}
		try {
			RemoteFolderTree remoteFolder =  (RemoteFolderTree) remoteFolders.get(folder.getName());
			
			if (remoteFolder == null){
				String repoPath = folder.getRepositoryRelativePath();
				IPath repoPath2 = new Path(repoPath);
				repoPath2 = repoPath2.removeFirstSegments(1);
				remoteFolder = (RemoteFolderTree) remoteFolders.get(repoPath2.toString());
			}
			
			//ok, we tried a short cut, but it wasn't meant to be so look at the entire tree
			if (remoteFolder == null)
				remoteFolder = remoteTree;
			
			
			ICVSRemoteResource[] children =remoteFolder.getChildren();
			ICVSResource resource = null;
			for (int i = 0; i < children.length; i++) {
				ICVSResource child = children[i];
				if (child.getName().equals(resourceName)) {
					resource = child;
					break;
				}
			}
			return getFileInfo(resource, new NullProgressMonitor());
		} catch (CoreException e) {
			// TODO Need to handle this somehow
			CVSProviderPlugin.log(e);
			return null;
		}
	}
	
	

}
