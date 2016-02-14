package org.jmo_client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.openstack4j.api.OSClient;
import org.openstack4j.api.exceptions.AuthenticationException;
import org.openstack4j.api.exceptions.ConnectionException;
import org.openstack4j.api.storage.ObjectStorageObjectService;
import org.openstack4j.model.common.Payloads;
import org.openstack4j.model.storage.object.SwiftContainer;
import org.openstack4j.model.storage.object.options.ContainerListOptions;
import org.openstack4j.model.storage.object.options.ObjectPutOptions;
import org.openstack4j.openstack.OSFactory;

public class Main {
	private static final String JMO_REPO = "JMO-Repository"; 
	private static final String PLUGINS_DIR = "plugins";
	private static final String SCRIPTS_DIR = "scripts";
	public static void main(String[] args) {
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		String cmd[] = null;
		File cfg = new File (System.getProperty("user.home") + File.separator + ".jmo" + File.separator + "credentials.properties");
		OSClient os = getOSclient(cfg);
		do{
			System.out.println("Please enter one of the following commands:\n"
					+ "config | upload-plugin | list | script-init | download | create-repo | help | quit\n");
			try {
				cmd = br.readLine().split(" ");
			} catch (IOException e) {
				e.printStackTrace();
			}
			switch(cmd[0]){
			case "upload-plugin":
				break;
			case "create-repo":
				createRepo(os);
				break;
			case "list":
				listPlugins(os);
				break;
			case "script-init":
				break;
			case "download":
				break;
			case "help":
				break;
			default:
				break;	
			case "config":
				createConfig(cfg);
				os = getOSclient(cfg);
				break;
			}
		}while(cmd[0].compareTo("quit") != 0);
		
		try {
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	/********************************************************************************************
	 * List the plugins in the JMO-Repository.
	 * @param os - The OpenStack client.
	 */
	private static void listPlugins(OSClient os) {
		List<? extends SwiftContainer> plugins = os.objectStorage().containers()
				.list(ContainerListOptions.create().path('/' + JMO_REPO + '/' + "plugins"));
		Iterator<? extends SwiftContainer> i = plugins.iterator();
		while (i.hasNext()){
			SwiftContainer plugin = i.next();
			System.out.println(plugin.getName());
		}
	}
	/********************************************************************************************
	 * Create the JMO-Repository container in Swift.
	 * @param os - The OpenStack client.
	 */
	private static void createRepo(OSClient os) {
		if ( os.objectStorage().containers().getMetadata(JMO_REPO).get("X-Timestamp") == null )
			os.objectStorage().containers().create(JMO_REPO);
		uploadDir(os,new File(PLUGINS_DIR));
		uploadDir(os, new File(SCRIPTS_DIR));
		
		os.objectStorage().objects().put(JMO_REPO, "jmo.jar", Payloads.create(new File("jmo.jar")));
		os.objectStorage().objects().put(JMO_REPO, "JMO-config.properties", Payloads.create(new File("JMO-config.properties")));
	}
	/********************************************************************************************
	 * Upload the given directory to the JMO-Repository.
	 * @param os - The OpenStack client.
	 * @param dir - The directory to upload.
	 */
	private static void uploadDir(OSClient os, File dir) {
		ObjectStorageObjectService swift = os.objectStorage().objects();
		File dPlugins = new File(PLUGINS_DIR);
		File pluginsList [] = dPlugins.listFiles();
		for (int i=0; i < pluginsList.length; i++){
			swift.put(JMO_REPO, pluginsList[i].getName(),
					Payloads.create(pluginsList[i]),
					ObjectPutOptions.create()
					.path('/' + "plugins"));
		}
	}
	/********************************************************************************************
	 * Creates a java properties file with the OpenStack credentials.
	 */
	private static void createConfig(File cfg) {
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		String aux = null;
		if (cfg.exists()){
			do{
			System.out.println("A config file already exists in " + cfg.getAbsolutePath() +". Do "
					+ "you want to overwrite it? (yes/no)");
			try {
				aux = br.readLine();
			} catch (IOException e) {
				e.printStackTrace();
			}
			}while (aux.compareTo("yes") != 0 && aux.compareTo("no") != 0);
		}
		if (aux == null || aux.compareTo("yes") == 0){
			cfg.getParentFile().mkdirs();
			OSCredentials cred = new OSCredentials();
			cred.askCredentials();
			writeConfig(cfg,cred);			
		}
		
		
	}
	/********************************************************************************************
	 * Writes the credentials in the given config file.
	 * @param cfg - File where to write.
	 * @param cred - The object containing the credentials. 
	 */
	private static void writeConfig(File cfg, OSCredentials cred) {
		if (cred.getUser() != null){
			try (PrintWriter fw = new PrintWriter (new FileOutputStream(cfg, false))){
				fw.println("#Openstack Username, Password and Tenant/Project");
				fw.println("user=" + cred.getUser()+"\n"
						+ "password=" + cred.getPassword()+"\n"
						+ "tenant=" + cred.getTenant());
				fw.println("#Keystone/Iddentity endpoint\n"
						+ "URLendpoint=" + cred.getKeystoneEndpoint());
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}else{
			System.out.println("Credentials are null.");
		}
	}
	/********************************************************************************************
	 * Returns an OSClient configures using the properties file passed as parameter.
	 * Note: OpenStack v2 authentication is used.
	 * @param cfgFile - the file properties used for the configuration of the client.
	 * The properties are: URLendpoint, user, tenant, password.
	 * @return The OSClient authenticated.
	 */
	private static OSClient getOSclient (File cfgFile) {
		OSClient os = null;
		try(InputStream is = new FileInputStream (cfgFile) ){
			Properties prop = new Properties();
			prop.load(is);
			os = OSFactory.builder()
					.endpoint(prop.getProperty("URLendpoint"))
					.credentials(prop.getProperty("user"),prop.getProperty("password"))
					.tenantName(prop.getProperty("tenant"))
					.authenticate();
		} catch (FileNotFoundException e) {
			System.out.println("*** Missing config file. Please run first the \"config\" command");
		} catch (IOException e) {
			e.printStackTrace();
		} catch (AuthenticationException e){
			System.out.println("Your credentials are invalid. Please run the \"config\" command with working credentials");
		} catch (ConnectionException e){
			System.out.println("*** Something went wrong contacting the Keystone service. Retry the configuration and double check the url");
		}
		return os;
	}
}