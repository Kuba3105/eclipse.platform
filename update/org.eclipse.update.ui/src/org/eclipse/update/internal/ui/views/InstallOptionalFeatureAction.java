/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.update.internal.ui.views;

import java.net.*;

import org.eclipse.core.runtime.*;
import org.eclipse.jface.action.*;
import org.eclipse.jface.dialogs.*;
import org.eclipse.jface.wizard.*;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.update.core.*;
import org.eclipse.update.internal.search.*;
import org.eclipse.update.internal.ui.*;
import org.eclipse.update.internal.ui.model.*;
import org.eclipse.update.internal.ui.wizards.*;
import org.eclipse.update.internal.api.operations.*;
import org.eclipse.update.internal.api.search.*;


public class InstallOptionalFeatureAction extends Action {
	private static final String KEY_OPTIONAL_INSTALL_TITLE = "FeaturePage.optionalInstall.title"; //$NON-NLS-1$

	private MissingFeature missingFeature;
	private Shell shell;

	public InstallOptionalFeatureAction(Shell shell, String text) {
		super(text);
		this.shell = shell;
	}

	public void setFeature(MissingFeature feature) {
		this.missingFeature = feature;
	}

	public void run() {
		if (missingFeature == null)
			return;
		
		IStatus status = OperationsManager.getValidator().validatePlatformConfigValid();
		if (status != null) {
			ErrorDialog.openError(
					UpdateUI.getActiveWorkbenchShell(),
					null,
					null,
					status);
			return;
		}
		
		// If current config is broken, confirm with the user to continue
		if (OperationsManager.getValidator().validateCurrentState() != null &&
				!confirm(UpdateUI.getString("Actions.brokenConfigQuestion")))
			return;
			
		
		VersionedIdentifier vid = missingFeature.getVersionedIdentifier();
		URL originatingURL = missingFeature.getOriginatingSiteURL();

		UpdateSearchScope scope = new UpdateSearchScope();
		scope.addSearchSite(originatingURL.toString(), originatingURL, null);

		OptionalFeatureSearchCategory category = new OptionalFeatureSearchCategory();
		category.addVersionedIdentifier(vid);
		final UpdateSearchRequest searchRequest =
			new UpdateSearchRequest(category, scope);

		BusyIndicator.showWhile(shell.getDisplay(), new Runnable() {
			public void run() {
				openWizard(searchRequest);
			}
		});
	}
	private void openWizard(UpdateSearchRequest searchRequest) {
		InstallWizard wizard = new InstallWizard(searchRequest);
		WizardDialog dialog = new ResizableWizardDialog(shell, wizard);
		dialog.create();
		dialog.getShell().setText(
			UpdateUI.getString(KEY_OPTIONAL_INSTALL_TITLE));
		dialog.getShell().setSize(600, 500);
		if (dialog.open() == IDialogConstants.OK_ID)
			UpdateUI.requestRestart(wizard.isRestartNeeded());
	}
	
	private boolean confirm(String message) {
		return MessageDialog.openConfirm(
			UpdateUI.getActiveWorkbenchShell(),
			UpdateUI.getString("FeatureStateAction.dialogTitle"), //$NON-NLS-1$
			message);
	}
}
