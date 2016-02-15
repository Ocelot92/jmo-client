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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

import org.apache.commons.io.FileUtils;
import org.openstack4j.api.OSClient;
import org.openstack4j.api.exceptions.AuthenticationException;
import org.openstack4j.api.storage.ObjectStorageObjectService;
import org.openstack4j.model.common.Payloads;
import org.openstack4j.model.storage.object.SwiftObject;
import org.openstack4j.model.storage.object.options.ObjectListOptions;
import org.openstack4j.model.storage.object.options.ObjectPutOptions;
import org.openstack4j.openstack.OSFactory;

public class Main {
	private static final String JMO_REPO = "test-Install"; 
	private static final String JMO_HOME = System.getProperty("user.dir");
	public static void main(String[] args) {

		File cfg = new File (JMO_HOME + File.separator + "credentials.properties");
		OSClient os = getOSclient(cfg);
		Scanner scan = new Scanner(System.in);
		String cmd = null;
		do{
			System.out.println("Please enter one of the following commands:\n"
					+ "config | upload-plugin | list | script-init | download | create-repo | help | quit\n");
			cmd = scan.next();
			switch(cmd){
			case "upload-plugin":
				ArrayList<File> files = new ArrayList<File>();
				while(scan.hasNext()){
					File f = new File(scan.next());
					files.add(f);
				}
				uploadPlugin(os, files);
				break;
			case "create-repo":
				createRepo(os);
				break;
			case "list":
				listPlugins(os);
				break;
			case "script-init":
				ArrayList<String> plugins = new ArrayList<String>();
				while(scan.hasNext()){
					String s = scan.next();
					plugins.add(s);
				}				
				prepareScriptInit(plugins, os);
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
				System.out.println("Configuration created in " + JMO_HOME);
				break;
			}
		}while(cmd.compareTo("quit") != 0);
		
		scan.close();
	}
	/********************************************************************************************
	 * Upload the given java class file to Swift and its optional scripts.
	 * @param os - The OpenStack client.
	 * @param files - A String array containing in location 0 the path of the java class file and 
	 * 				in the successive  eventually locations any scripts specified.
	 */
	private static void uploadPlugin(OSClient os, ArrayList<File> files) {
		if(!files.get(0).exists()){
			System.out.println("Error retriving the class file.");
		}else{
			os.objectStorage().objects().put(JMO_REPO, files.get(0).getName(),
					Payloads.create(files.get(0)),
					ObjectPutOptions.create()
					.path("plugins"));
			//upload eventually scripts
			for(int i=1; i < files.size(); i++){
				if (!files.get(i).exists())
					System.out.println("Error retriving " + files.get(i).getName());
				else
					os.objectStorage().objects().put(JMO_REPO, files.get(i).getName(),
							Payloads.create(files.get(i)),
							ObjectPutOptions.create()
							.path("scripts"));
			}
		}
	}
	/********************************************************************************************
	 * List the plugins in the JMO repository on Swift.
	 * @param os - The OpenStack client.
	 */
	private static void listPlugins(OSClient os) {
		List<? extends SwiftObject> plugins = os.objectStorage().objects()
				.list(JMO_REPO, ObjectListOptions.create().path("plugins"));
		Iterator<? extends SwiftObject> i = plugins.iterator();
		SwiftObject plugin = null;
		while (i.hasNext()){
			 plugin = i.next();
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
		uploadDir(os, new File(JMO_HOME + File.separator + "plugins"));
		uploadDir(os, new File(JMO_HOME + File.separator + "scripts"));
		String pathJMOjar = JMO_HOME + File.separator + "jmo.jar";
		String pathJMOprop = JMO_HOME + File.separator + "JMO-config.properties";
		os.objectStorage().objects().put(JMO_REPO, "jmo.jar", Payloads.create(new File(pathJMOjar)));
		os.objectStorage().objects().put(JMO_REPO, "JMO-config.properties", Payloads.create(new File(pathJMOprop)));
	}
	/********************************************************************************************
	 * Upload the given directory to the JMO-Repository.
	 * @param os - The OpenStack client.
	 * @param dir - The directory to upload.
	 */
	private static void uploadDir(OSClient os, File dir) {
		ObjectStorageObjectService swift = os.objectStorage().objects();
		File pluginsList [] = dir.listFiles();
		for (int i=0; i < pluginsList.length; i++){
			swift.put(JMO_REPO, pluginsList[i].getName(),
					Payloads.create(pluginsList[i]),
					ObjectPutOptions.create()
					.path("/plugins"));
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
						+ "KeystoneEndpoint=" + cred.getKeystoneEndpoint());
				fw.print("#Swift endpoint\n"
						+ "SwiftEndpoint="+ cred.getSwiftEndpoint());
				
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
					.endpoint(prop.getProperty("KeystoneEndpoint"))
					.credentials(prop.getProperty("user"),prop.getProperty("password"))
					.tenantName(prop.getProperty("tenant"))
					.authenticate();
		} catch (FileNotFoundException e) {
			System.out.println("*** Missing config file. Please run first the \"config\" command");
		} catch (IOException e) {
			e.printStackTrace();
		} catch (AuthenticationException e){
			System.out.println("Your credentials are invalid. Please run the \"config\" command with working credentials");
		}
		return os;
	}
	/********************************************************************************************
	 * Creates the init script to submit for the Cloud-Init at the instance creation.
	 * @param plugins 
	 */
	private static void prepareScriptInit(ArrayList<String> plugins, OSClient os) {
		try {			
			ObjectStorageObjectService swift = os.objectStorage().objects();
			String plgPicking = "";
			for (String str : plugins){
				plgPicking += "downloadFile $JMO_CONTAINER/plugins/" + str + ".class ./plugins/" + str + ".class \n";
				List<? extends SwiftObject> script = swift.list(JMO_REPO, ObjectListOptions.create()
						.path("scripts")
						.startsWith(str));
				if (script.size() != 0){
					plgPicking += "downloadFile $JMO_CONTAINER/scripts/" + str + ".sh ./scripts/" + str + ".sh \n";
				}
			}
			genetareInitScript(plgPicking);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	/********************************************************************************************
	 * Create the jmo-init.sh script from a template script.
	 * @param plgPicking - plugins section of the script.
	 * @throws IOException in case of an I/O error.
	 */
	private static void genetareInitScript(String plgPicking) throws IOException {
		InputStream is = new FileInputStream("credentials.properties");
		Properties prop = new Properties();
		prop.load(is);
		is.close();
		File initTemplate = new File(".jmo-init-template.sh");
		String initString = FileUtils.readFileToString(initTemplate);
		initString = initString.replace("<user>", prop.getProperty("user"));
		initString = initString.replace("<tenant>", prop.getProperty("tenant"));
		initString = initString.replace("<password>", prop.getProperty("password"));
		initString = initString.replace("<swift>", prop.getProperty("SwiftEndpoint"));
		initString = initString.replace("<keystone>", prop.getProperty("KeystoneEndpoint"));
		initString = initString.replace("<plugins>", plgPicking);
		File initScript = new File ("jmo-init.sh");
		FileUtils.writeStringToFile(initScript, initString);
	}
}