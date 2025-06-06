/*******************************************************************************
 *  Copyright (c) 2000, 2025 IBM Corporation and others.
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
 *******************************************************************************/
package org.eclipse.pde.internal.ui.parts;

import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.osgi.util.NLS;
import org.eclipse.pde.internal.ui.PDEUIMessages;
import org.eclipse.pde.internal.ui.shared.CachedCheckboxTableViewer;
import org.eclipse.pde.internal.ui.util.SWTUtil;
import org.eclipse.pde.internal.ui.wizards.ListUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.forms.widgets.FormToolkit;

public class WizardCheckboxTablePart extends CheckboxTablePart {
	private int selectAllIndex = -1;
	private int deselectAllIndex = -1;
	private int selectIndex = -1;
	private int deselectIndex = -1;
	private final String tableName;
	private Label counterLabel;

	/**
	 * Constructor for WizardCheckboxTablePart.
	 */
	public WizardCheckboxTablePart(String tableName, String[] buttonLabels) {
		super(buttonLabels);
		this.tableName = tableName;
	}

	public WizardCheckboxTablePart(String mainLabel) {
		this(mainLabel, new String[] {PDEUIMessages.WizardCheckboxTablePart_selectAll, PDEUIMessages.WizardCheckboxTablePart_deselectAll, PDEUIMessages.WizardCheckboxTablePart_select, PDEUIMessages.WizardCheckboxTablePart_deselect});
		setSelectAllIndex(0);
		setDeselectAllIndex(1);
		setSelectIndex(2);
		setDeselectIndex(3);
	}

	public void setSelectAllIndex(int index) {
		this.selectAllIndex = index;
	}

	public void setDeselectAllIndex(int index) {
		this.deselectAllIndex = index;
	}

	public void setSelectIndex(int index) {
		this.selectIndex = index;
	}

	public void setDeselectIndex(int index) {
		this.deselectIndex = index;
	}

	@Override
	protected void buttonSelected(Button button, int index) {
		if (index == selectAllIndex) {
			handleSelectAll(true);
		}
		if (index == deselectAllIndex) {
			handleSelectAll(false);
		}
		if (index == selectIndex) {
			handleSelect(true);
		}
		if (index == deselectIndex) {
			handleSelect(false);
		}
	}

	public Object[] getSelection() {
		CheckboxTableViewer viewer = getTableViewer();
		return viewer.getCheckedElements();
	}

	public void setSelection(Object[] selected) {
		CheckboxTableViewer viewer = getTableViewer();
		viewer.setCheckedElements(selected);
		updateCounterLabel();
	}

	public void createControl(Composite parent) {
		createControl(parent, 2);
	}

	public void createControl(Composite parent, int span) {
		createControl(parent, SWT.NULL, span, null);
		counterLabel = new Label(parent, SWT.NULL);
		GridData gd = new GridData(GridData.VERTICAL_ALIGN_BEGINNING | GridData.HORIZONTAL_ALIGN_FILL);
		gd.horizontalSpan = span;
		counterLabel.setLayoutData(gd);
		updateCounterLabel();
	}

	public void createControl(Composite parent, int span, boolean multiselect) {
		if (multiselect) {
			createControl(parent, SWT.MULTI, span, null);
		} else {
			createControl(parent, SWT.NULL, span, null);
		}
		counterLabel = new Label(parent, SWT.NULL);
		GridData gd = new GridData(GridData.VERTICAL_ALIGN_BEGINNING | GridData.HORIZONTAL_ALIGN_FILL);
		gd.horizontalSpan = span;
		counterLabel.setLayoutData(gd);
		updateCounterLabel();
	}

	@Override
	protected Button createButton(Composite parent, String label, int index, FormToolkit toolkit) {
		Button button = super.createButton(parent, label, index, toolkit);
		SWTUtil.setButtonDimensionHint(button);
		return button;
	}

	@Override
	protected StructuredViewer createStructuredViewer(Composite parent, int style, FormToolkit toolkit) {
		StructuredViewer viewer = super.createStructuredViewer(parent, style, toolkit);
		viewer.setComparator(ListUtil.NAME_COMPARATOR);
		return viewer;
	}

	@Override
	protected void createMainLabel(Composite parent, int span, FormToolkit toolkit) {
		if (tableName == null) {
			return;
		}
		Label label = new Label(parent, SWT.NULL);
		label.setText(tableName);
		GridData gd = new GridData();
		gd.horizontalSpan = span;
		label.setLayoutData(gd);
	}

	public void updateCounterLabel() {
		String number = "" + getSelectionCount(); //$NON-NLS-1$
		String totalNumber = "" + getTotalCount(); //$NON-NLS-1$
		String message = NLS.bind(PDEUIMessages.WizardCheckboxTablePart_counter, (new String[] {number, totalNumber}));
		counterLabel.setText(message);
	}

	public int getSelectionCount() {
		CachedCheckboxTableViewer viewer = getTableViewer();
		if (viewer == null) {
			return 0;
		}
		return viewer.getUnfilteredCheckedCount();
	}

	public void selectAll(boolean select) {
		handleSelectAll(select);
	}

	private int getTotalCount() {
		CheckboxTableViewer viewer = getTableViewer();
		if (viewer == null) {
			return 0;
		}

		IStructuredContentProvider contentProvider = (IStructuredContentProvider) viewer.getContentProvider();
		if (contentProvider == null) {
			return 0;
		}
		return contentProvider.getElements(viewer.getInput()).length;
	}

	protected void handleSelectAll(boolean select) {
		CheckboxTableViewer viewer = getTableViewer();
		viewer.setAllChecked(select);
		updateCounterLabel();
	}

	protected void handleSelect(boolean select) {
		CheckboxTableViewer viewer = getTableViewer();
		if (viewer.getTable().getSelection().length > 0) {
			TableItem[] selected = viewer.getTable().getSelection();
			for (TableItem selectedItem : selected) {
				selectedItem.setChecked(select);
			}
			updateCounterLabel();
		}
	}

	@Override
	protected void elementChecked(Object element, boolean checked) {
		updateCounterLabel();
	}

	public Label getCounterLabel() {
		return counterLabel;
	}
}
