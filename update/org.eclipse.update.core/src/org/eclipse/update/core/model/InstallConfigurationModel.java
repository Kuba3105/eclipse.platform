package org.eclipse.update.core.model;
/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.ResourceBundle;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.update.internal.core.URLEncoder;
import org.eclipse.update.internal.core.UpdateManagerPlugin;
import org.xml.sax.SAXException;

/**
 * An InstallConfigurationModel is 
 * 
 */

public class InstallConfigurationModel extends ModelObject {
	/**
	 * initialize the configurations from the persistent model.
	 */
	public void initialize() throws CoreException {
		try {
			URL resolvedURL = URLEncoder.encode(getURL());
			new InstallConfigurationParser(resolvedURL.openStream(), this);
		} catch (FileNotFoundException exception) {
			// file doesn't exist, ok, log it and continue 
			// log no config
			if (UpdateManagerPlugin.DEBUG && UpdateManagerPlugin.DEBUG_SHOW_WARNINGS) {
				UpdateManagerPlugin.getPlugin().debug(getLocationURLString() + " does not exist, the local site is not in synch with the filesystem and is pointing to a file that doesn't exist.");
			}
		} catch (SAXException exception) {
			String id = UpdateManagerPlugin.getPlugin().getDescriptor().getUniqueIdentifier();
			IStatus status = new Status(IStatus.ERROR, id, IStatus.OK, "Parsing error during creation of configuration:" + getLocationURLString()+"\r\n"+exception.toString(), exception);
			throw new CoreException(status);
		} catch (IOException exception) {
			String id = UpdateManagerPlugin.getPlugin().getDescriptor().getUniqueIdentifier();
			IStatus status = new Status(IStatus.ERROR, id, IStatus.OK, "Error during file access :", exception);
			throw new CoreException(status);
		}
	}

	private boolean isCurrent;
	private URL locationURL;
	private String locationURLString;	
	private Date date;
	private String label;
	private List /* of ConfiguretionActivityModel */activities;
	private List /* of configurationSiteModel */ configurationSites;

	/**
	 * default constructor. Create
	 */
	public InstallConfigurationModel(){
	}
	
	/**
	 * @since 2.0
	 */
	public ConfigurationSiteModel[] getConfigurationSitesModel() {
		if (configurationSites == null)
			return new ConfigurationSiteModel[0];
			
		return (ConfigurationSiteModel[]) configurationSites.toArray(arrayTypeFor(configurationSites));
	}

	/**
	 * Adds the configuration to the list
	 * is called when adding a Site or parsing the XML file
	 * in this case we do not want to create a new activity, so we do not want t call
	 * addConfigurationSite()
	 */
	public void addConfigurationSiteModel(ConfigurationSiteModel site) {
		if (configurationSites == null) {
			configurationSites = new ArrayList();
		}
		if (!configurationSites.contains(site))
			configurationSites.add(site);
	}
	

	public void setConfigurationSiteModel(ConfigurationSiteModel[] sites) {
		configurationSites=null;
		for (int i = 0; i < sites.length; i++) {
			addConfigurationSiteModel(sites[i]);
		}
	}
	
	/**
	 * @since 2.0
	 */
	public boolean removeConfigurationSiteModel(ConfigurationSiteModel site) {
		if (!isCurrent)
			return false;
			
		//FIXME: remove should make sure we synchronize
		if (configurationSites != null) {
			return configurationSites.remove(site);
		}
		
		return false;
	}
	
	/**
	 * @since 2.0
	 */
	public boolean isCurrent() {
		return isCurrent;
	}
	
	/**
	 *  @since 2.0
	 */
	public void setCurrent(boolean isCurrent) {
		// FIXME I do not believe we should check
		// otherwise we can never set as Current
		//assertIsWriteable();
		this.isCurrent = isCurrent;
	}
	
		
	
	/**
	 * @since 2.0
	 */
	public ConfigurationActivityModel[] getActivityModel() {
	if (activities==null)
			return new ConfigurationActivityModel[0];
	return (ConfigurationActivityModel[]) activities.toArray(arrayTypeFor(activities));
	}
	
	/**
	 * @since 2.0
	 */
	public void addActivityModel(ConfigurationActivityModel activity) {
		if (activities == null)
			activities = new ArrayList();
		if (!activities.contains(activity)){
			activities.add(activity);
			activity.setInstallConfigurationModel(this);
		}
	}
	/**
	 * 
	 */
	public Date getCreationDate() {
		return date;
	}
	/**
	 * Sets the date.
	 * @param date The date to set
	 */
	public void setCreationDate(Date date) {
		assertIsWriteable();
		this.date = date;
	}
	/**
	 * @since 2.0
	 */
	public URL getURL() {
		return locationURL;
	}
	
	/**
	 * @since 2.0
	 */
	public String getLabel() {
		return label;
	}
	
	/**
	 * Sets the label.
	 * @param label The label to set
	 */
	public void setLabel(String label) {
		assertIsWriteable();
		this.label = label;
	}
	
	/**
	 * Gets the locationURLString.
	 * @return Returns a String
	 */
	public String getLocationURLString() {
		return locationURLString;
	}

	/**
	 * Sets the locationURLString.
	 * @param locationURLString The locationURLString to set
	 */
	public void setLocationURLString(String locationURLString) {
		assertIsWriteable();
		this.locationURLString = locationURLString;
		this.locationURL = null;
	}


	/*
	 * @see ModelObject#resolve(URL, ResourceBundle)
	 */
	public void resolve(URL base, ResourceBundle bundle)
		throws MalformedURLException {
		// local
		locationURL = resolveURL(base,bundle,locationURLString);
		
		// delagate
		resolveListReference(getActivityModel(),base,bundle);
		resolveListReference(getConfigurationSitesModel(),base,bundle);
	}

}