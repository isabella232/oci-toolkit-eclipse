/**
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
 */
package com.oracle.oci.eclipse.ui.explorer.database;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.equinox.security.storage.StorageException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWizard;

import com.oracle.bmc.database.model.CreateAutonomousDatabaseBase.DbWorkload;
import com.oracle.bmc.database.model.CreateAutonomousDatabaseDetails;
import com.oracle.bmc.database.model.CreateAutonomousDatabaseDetails.Builder;
import com.oracle.oci.eclipse.ErrorHandler;
import com.oracle.oci.eclipse.account.PreferencesWrapper;
import com.oracle.oci.eclipse.sdkclients.ADBInstanceClient;

public class CreateADBWizard  extends Wizard implements INewWizard {

    private CreateADBWizardPage page;
    private ISelection selection;
    DbWorkload workloadType;
//    private CreateADBAdvancedWizardPage advancedPage;

	public CreateADBWizard(DbWorkload workloadType) {
		super();
		this.workloadType = workloadType;
		setNeedsProgressMonitor(true);
	}

    @Override
    public void addPages() {
        page = new CreateADBWizardPage(selection, workloadType);
        addPage(page);
//        advancedPage = new CreateADBAdvancedWizardPage(selection, workloadType);
//        addPage(advancedPage);
    }

    /**
     * This method is called when 'Finish' button is pressed in
     * the wizard. We will create an operation and run it
     * using wizard as execution context.
     */
    @Override
    public boolean performFinish() {
    	
    	if(!isValidPassword())
    		return false;
    	
    	if(!isValidContainerDatabase()) {
    		return false;
    	}
    	
    	final String compartmentId = page.getADBCompartmentId();
        
    	Builder createADBRequestBuilder = 
        CreateAutonomousDatabaseDetails.builder()
        .compartmentId(compartmentId)
        .cpuCoreCount(Integer.valueOf(page.getCPUCoreCount()))
        .dataStorageSizeInTBs(Integer.valueOf(page.getStorageInTB()))
        .displayName(page.getDisplayName())
        .adminPassword(page.getAdminPassword())
        .dbName(page.getDatabaseName())
        .dbWorkload(workloadType)
        .licenseModel(page.getLicenseModel());
		
		final CreateAutonomousDatabaseDetails createADBRequest;
		final boolean isDedicated = page.isDedicatedInfra();
		if (isDedicated) {
			createADBRequest = createADBRequestBuilder.isDedicated(isDedicated)
					.autonomousContainerDatabaseId(page.getSelectedContainerDbId()).build();
		} else {
			createADBRequest = createADBRequestBuilder.isAutoScalingEnabled(page.isAutoScalingEnabled())
					.isFreeTier(page.isAlwaysFreeInstance()).build();
		}
    	
        IRunnableWithProgress op = new IRunnableWithProgress() {
            @Override
            public void run(IProgressMonitor monitor) throws InvocationTargetException {
                ADBInstanceClient.getInstance().createInstance(createADBRequest);
                monitor.done();
            }
        };
        try {
            getContainer().run(true, true, op);
        } catch (InterruptedException e) {
            return false;
        } catch (InvocationTargetException e) {
            Throwable realException = e.getTargetException();
            MessageDialog.openError(getShell(), "Failed to Create ADB instance ", realException.getMessage());
            return false;
        }

        // if that worked, store the admin password if checked
        if (page.isStoreAdminPassword())
        {
            String key = PreferencesWrapper.createSecurePreferenceKey(page.getADBCompartmentId(), page.getDatabaseName());
            try {
                PreferencesWrapper.getSecurePreferences().put(key, page.getAdminPassword(), true);
            } catch (StorageException e) {
               ErrorHandler.logErrorStack("Error storing admin password", e);
            }
        }
        
        return true;
    }

    /**
     * We will accept the selection in the workbench to see if
     * we can initialize from it.
     * @see IWorkbenchWizard#init(IWorkbench, IStructuredSelection)
     */
    @Override
    public void init(IWorkbench workbench, IStructuredSelection selection) {
        this.selection = selection;
    }
    
    private boolean isValidPassword() {
    	final String adminPassword = page.getAdminPassword();
        final String confirmAdminPassword = page.getConfirmAdminPassword();
        
        if(adminPassword == null || adminPassword.trim().equals("")) {
        	MessageDialog.openError(getShell(), "Admin password required error",
					"Admin password cannot be empty");
			return false;
        	
        } else if (!adminPassword.equals(confirmAdminPassword)) {
			MessageDialog.openError(getShell(), "Admin password mismatch error",
					"Confirm Admin password must match Admin password");
			return false;
		}
		
		return true;
    }
    
	private boolean isValidContainerDatabase() {
		if (page.isDedicatedInfra()
				&& (page.getSelectedContainerDbId() == null || "".equals(page.getSelectedContainerDbId()))) {
			MessageDialog.openError(getShell(), "Container Database ID required error",
					"The Autonomous Database's Container Database ID cannot be null.");
			return false;
		}
		
		return true;
	}
}
