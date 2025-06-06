/*******************************************************************************
 *  Copyright (c) 2005, 2015 IBM Corporation and others.
 *
 *  This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License 2.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/legal/epl-2.0/
 *
 *  SPDX-License-Identifier: EPL-2.0
 *
 *  Contributors:
 *     IBM Corporation - initial API and implementation
 *     Peter Friese <peter.friese@gentleware.com> - bug 201956
 *******************************************************************************/
package org.eclipse.pde.internal.ui.editor.product;

import org.eclipse.core.resources.IFile;
import org.eclipse.equinox.bidi.StructuredTextTypeHandlerFactory;
import org.eclipse.jface.util.BidiUtils;
import org.eclipse.jface.window.Window;
import org.eclipse.pde.core.IModelChangedEvent;
import org.eclipse.pde.internal.core.iproduct.IAboutInfo;
import org.eclipse.pde.internal.core.iproduct.IProduct;
import org.eclipse.pde.internal.core.iproduct.IProductModel;
import org.eclipse.pde.internal.ui.PDEPlugin;
import org.eclipse.pde.internal.ui.PDEUIMessages;
import org.eclipse.pde.internal.ui.editor.EditorUtilities;
import org.eclipse.pde.internal.ui.editor.FormEntryAdapter;
import org.eclipse.pde.internal.ui.editor.FormLayoutFactory;
import org.eclipse.pde.internal.ui.editor.PDEFormPage;
import org.eclipse.pde.internal.ui.editor.PDESection;
import org.eclipse.pde.internal.ui.editor.validation.TextValidator;
import org.eclipse.pde.internal.ui.parts.FormEntry;
import org.eclipse.pde.internal.ui.util.FileExtensionsFilter;
import org.eclipse.pde.internal.ui.util.FileValidator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.dialogs.ElementTreeSelectionDialog;
import org.eclipse.ui.forms.events.HyperlinkEvent;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.Section;
import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;

public class AboutSection extends PDESection {

	private FormEntry fImageEntry;
	private FormEntry fTextEntry;

	private TextValidator fImageEntryValidator;

	public AboutSection(PDEFormPage page, Composite parent) {
		super(page, parent, Section.DESCRIPTION);
		createClient(getSection(), page.getEditor().getToolkit());
	}

	@Override
	protected void createClient(Section section, FormToolkit toolkit) {
		section.setLayout(FormLayoutFactory.createClearGridLayout(false, 1));
		GridData data = new GridData(GridData.FILL_BOTH);
		data.widthHint = 300;
		data.heightHint = 70;
		section.setLayoutData(data);

		section.setText(PDEUIMessages.AboutSection_title);
		section.setDescription(PDEUIMessages.AboutSection_desc);

		Composite client = toolkit.createComposite(section);
		client.setLayout(FormLayoutFactory.createSectionClientGridLayout(false, 3));
		client.setLayoutData(new GridData(GridData.FILL_BOTH));

		IActionBars actionBars = getPage().getPDEEditor().getEditorSite().getActionBars();
		fImageEntry = new FormEntry(client, toolkit, PDEUIMessages.AboutSection_image, PDEUIMessages.AboutSection_browse, isEditable());
		BidiUtils.applyBidiProcessing(fImageEntry.getText(), StructuredTextTypeHandlerFactory.FILE);
		fImageEntry.setEditable(isEditable());
		// Create validator
		fImageEntryValidator = new TextValidator(getManagedForm(), fImageEntry.getText(), getProject(), true) {
			@Override
			protected boolean validateControl() {
				return validateImageEntry();
			}
		};
		fImageEntry.setFormEntryListener(new FormEntryAdapter(this, actionBars) {
			@Override
			public void textValueChanged(FormEntry entry) {
				getAboutInfo().setImagePath(entry.getValue());
			}

			@Override
			public void browseButtonSelected(FormEntry entry) {
				handleBrowse();
			}

			@Override
			public void linkActivated(HyperlinkEvent e) {
				EditorUtilities.openImage(fImageEntry.getValue(), getProduct().getDefiningPluginId());
			}
		});

		// Text field
		// Create Text field UI
		int style = SWT.MULTI | SWT.WRAP | SWT.V_SCROLL;
		fTextEntry = new FormEntry(client, toolkit, PDEUIMessages.AboutSection_text, style);
		// Configure Text widget
		data = new GridData(GridData.FILL_BOTH);
		data.horizontalSpan = 2;
		// Needed to align vertically with form entry field and allow space
		// for a possible field decoration
		data.horizontalIndent = FormLayoutFactory.CONTROL_HORIZONTAL_INDENT;
		fTextEntry.getText().setLayoutData(data);
		// Configure Label widget to be aligned to the top-left
		data = new GridData(SWT.BEGINNING, SWT.BEGINNING, false, false);
		fTextEntry.getLabel().setLayoutData(data);
		// Configure editability
		fTextEntry.setEditable(isEditable());
		// Create Text field listener
		fTextEntry.setFormEntryListener(new FormEntryAdapter(this, actionBars) {
			@Override
			public void textValueChanged(FormEntry entry) {
				getAboutInfo().setText(entry.getValue());
			}
		});

		toolkit.paintBordersFor(client);
		section.setClient(client);
		// Register to be notified when the model changes
		getModel().addModelChangedListener(this);
	}

	private boolean validateImageEntry() {
		return EditorUtilities.imageEntrySizeDoesNotExceed(fImageEntryValidator, fImageEntry, getProduct(), 500, 330, 250, 330);
	}

	private void handleBrowse() {
		ElementTreeSelectionDialog dialog = new ElementTreeSelectionDialog(getSection().getShell(), new WorkbenchLabelProvider(), new WorkbenchContentProvider());

		dialog.setValidator(new FileValidator());
		dialog.setAllowMultiple(false);
		dialog.setTitle(PDEUIMessages.AboutSection_imgTitle);
		dialog.setMessage(PDEUIMessages.AboutSection_imgMessage);
		FileExtensionsFilter filter = new FileExtensionsFilter();
		filter.addFileExtension("gif"); //$NON-NLS-1$
		filter.addFileExtension("png"); //$NON-NLS-1$
		dialog.addFilter(filter);
		dialog.setInput(PDEPlugin.getWorkspace().getRoot());

		if (dialog.open() == Window.OK) {
			IFile file = (IFile) dialog.getFirstResult();
			fImageEntry.setValue(file.getFullPath().toString());
		}
	}

	@Override
	public void refresh() {
		fImageEntry.setValue(getAboutInfo().getImagePath(), true);
		fTextEntry.setValue(getAboutInfo().getText(), true);
		super.refresh();
	}

	@Override
	public void commit(boolean onSave) {
		fImageEntry.commit();
		fTextEntry.commit();
		super.commit(onSave);
	}

	@Override
	public void cancelEdit() {
		fImageEntry.cancelEdit();
		fTextEntry.cancelEdit();
		super.cancelEdit();
	}

	private IAboutInfo getAboutInfo() {
		IAboutInfo info = getProduct().getAboutInfo();
		if (info == null) {
			info = getModel().getFactory().createAboutInfo();
			getProduct().setAboutInfo(info);
		}
		return info;
	}

	private IProduct getProduct() {
		return getModel().getProduct();
	}

	private IProductModel getModel() {
		return (IProductModel) getPage().getPDEEditor().getAggregateModel();
	}

	@Override
	public boolean canPaste(Clipboard clipboard) {
		Display d = getSection().getDisplay();
		Control c = d.getFocusControl();
		if (c instanceof Text) {
			return true;
		}
		return false;
	}

	@Override
	public void modelChanged(IModelChangedEvent e) {
		// No need to call super, handling world changed event here
		if (e.getChangeType() == IModelChangedEvent.WORLD_CHANGED) {
			handleModelEventWorldChanged(e);
		}
	}

	private void handleModelEventWorldChanged(IModelChangedEvent event) {
		refresh();
	}

	@Override
	public void dispose() {
		IProductModel model = getModel();
		if (model != null) {
			model.removeModelChangedListener(this);
		}
		super.dispose();
	}
}
