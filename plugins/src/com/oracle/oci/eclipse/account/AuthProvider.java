/**
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
 */
package com.oracle.oci.eclipse.account;

import com.oracle.bmc.ClientRuntime;
import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.identity.model.AuthToken;
import com.oracle.oci.eclipse.ErrorHandler;
import com.oracle.oci.eclipse.sdkclients.IdentClient;
import com.oracle.oci.eclipse.ui.account.ClientUpdateManager;


public class AuthProvider {

	public static final String ROOT_COMPARTMENT_NAME = "[Root Compartment]";
    private static AuthProvider single_instance = null;

    private static AuthenticationDetailsProvider provider;
    private String currentProfileName = PreferencesWrapper.getProfile();
    private String currentConfigFileName = PreferencesWrapper.getConfigFileName();
    private String currentRegionName = PreferencesWrapper.getRegion();
    private String currentCompartmentId;
    private String currentCompartmentName = ROOT_COMPARTMENT_NAME;

    public static AuthProvider getInstance()
    {
        if (single_instance == null) {
            single_instance = new AuthProvider();
        }
        return single_instance;
    }

    private AuthProvider() {
    }

    private AuthenticationDetailsProvider createProvider() {
        try {
            provider =
                    new ConfigFileAuthenticationDetailsProvider(currentConfigFileName, currentProfileName);
        } catch (Exception e) {
            ErrorHandler.logErrorStack(e.getMessage(), e);
        }
        currentCompartmentId = provider.getTenantId();
        setClientUserAgent();
        return provider;
    }

    public AuthenticationDetailsProvider getProvider() {
        if (provider == null) {
            provider = createProvider();
        }
        else {
            String newProfile = PreferencesWrapper.getProfile();
            String newConfigFileName = PreferencesWrapper.getConfigFileName();
            currentRegionName = PreferencesWrapper.getRegion();

            if ((!newProfile.equals(currentProfileName)) || (!newConfigFileName.endsWith(currentConfigFileName))) {
                currentConfigFileName = newConfigFileName;
                currentProfileName = newProfile;
                try {
                    provider = new ConfigFileAuthenticationDetailsProvider(currentConfigFileName, currentProfileName);
                    ErrorHandler.logInfo(currentProfileName + " " + currentConfigFileName + " " + currentRegionName + " "
                            + currentCompartmentId);
                } catch (Exception e) {
                    ErrorHandler.reportException("Error connecting to: " + currentProfileName + " " + e.getMessage(), e);
                }
            }
        }
        return provider;
    }

    public void updateCompartmentId(String compartmentId) {
        ClientUpdateManager.getInstance().getSupportViews().firePropertyChange("currentCompartmentId",
                this.currentCompartmentId, compartmentId);
        currentCompartmentId = compartmentId;
    }

    public void setCompartmentId(String compartmentId) {
        currentCompartmentId = compartmentId;
    }

    public String getCompartmentId() {
        return currentCompartmentId;
    }
    
    public void setCompartmentName(String currentCompartmentName) {
    	this.currentCompartmentName = currentCompartmentName;
    }

    public String getCompartmentName() {
    	return currentCompartmentName;
    }

    public void updateRegion(String regionId) {
        PreferencesWrapper.setRegion(regionId);
        ClientUpdateManager.getInstance().getSupportRegion().firePropertyChange("currentRegionName",
                this.currentRegionName, regionId);
        ClientUpdateManager.getInstance().getSupportViews().firePropertyChange("currentRegionName",
                this.currentRegionName, regionId);
        currentRegionName = regionId;
    }

    public Region getRegion() {
        return Region.fromRegionId(currentRegionName);
    }

    public void deleteAuthToken() {
        if (!PreferencesWrapper.getAuthToken().isEmpty()) {
            AuthToken authToken = null;
            PreferencesWrapper.setAuthToken(authToken);
        }
    }
    public String getAuthToken() {
        if (PreferencesWrapper.getAuthToken().isEmpty()) {
            try {
                AuthToken token = IdentClient.getInstance().createAuthToken();
                if(token != null) {
                    PreferencesWrapper.setAuthToken(token);
                    return token.getToken();
                } else {
                    ErrorHandler.logError("Null token!");
                }
            } catch (Exception e) {
                ErrorHandler.reportAndShowException("Failed to get OCI Auth Token. ", e);
            }

        } else {
            return PreferencesWrapper.getAuthToken();
        }
        return "";
    }

    // set the plug-in version into the SDK.
    private void setClientUserAgent() {
        ErrorHandler.logInfo("Setting SDK ClientUserAgent to: "+ PreferencesWrapper.getUserAgent());
        ClientRuntime.setClientUserAgent(PreferencesWrapper.getUserAgent());
    }

    @Override
    public void finalize() throws Throwable{
        single_instance = null;
    }

}
