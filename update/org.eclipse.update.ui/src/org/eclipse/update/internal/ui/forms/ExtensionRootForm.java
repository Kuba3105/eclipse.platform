package org.eclipse.update.internal.ui.forms;
/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import org.eclipse.swt.widgets.*;
import org.eclipse.ui.help.WorkbenchHelp;
import org.eclipse.update.internal.ui.UpdateUIPlugin;
import org.eclipse.update.internal.ui.model.*;
import org.eclipse.update.internal.ui.pages.UpdateFormPage;
import org.eclipse.update.ui.forms.internal.*;
import org.eclipse.update.ui.forms.internal.engine.FormEngine;

public class ExtensionRootForm extends UpdateWebForm {
	private SiteBookmark currentBookmark;
	private static final String KEY_TITLE = "ExtensionRootPage.title";
	private static final String KEY_DESC = "ExtensionRootPage.desc";
	private Label path;

	public ExtensionRootForm(UpdateFormPage page) {
		super(page);
	}

	public void dispose() {
		super.dispose();
	}

	public void initialize(Object modelObject) {
		setHeadingText(UpdateUIPlugin.getResourceString(KEY_TITLE));
		super.initialize(modelObject);
	}

	protected void createContents(Composite parent) {
		HTMLTableLayout layout = new HTMLTableLayout();
		parent.setLayout(layout);
		layout.leftMargin = layout.rightMargin = 10;
		layout.topMargin = 10;
		layout.verticalSpacing = 20;
		// defect 13686
		//layout.horizontalSpacing = 0;
		layout.numColumns = 3;

		FormWidgetFactory factory = getFactory();

		path = factory.createHeadingLabel(parent, "");
		TableData td = new TableData();
		td.colspan = 3;
		path.setLayoutData(td);

		FormEngine engine = factory.createFormEngine(parent);
		String markup = UpdateUIPlugin.getResourceString(KEY_DESC);
		markup = "<form>"+markup+"</form>";
		engine.load(markup, true, true);
		td = new TableData();
		td.colspan = 3;
		setFocusControl(engine);
		engine.setLayoutData(td);
		WorkbenchHelp.setHelp(parent, "org.eclipse.update.ui.ExtensionRootForm");
	}

	public void expandTo(Object obj) {
		if (obj instanceof ExtensionRoot) {
			inputChanged((ExtensionRoot) obj);
		}
	}

	private void inputChanged(ExtensionRoot extensionRoot) {
		path.setText(extensionRoot.getInstallableDirectory().getPath());
		path.getParent().layout();
	}
}