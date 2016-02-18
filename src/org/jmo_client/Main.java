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
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

import org.apache.commons.io.FileUtils;
import org.openstack4j.api.OSClient;
import org.openstack4j.api.exceptions.AuthenticationException;
import org.openstack4j.api.storage.ObjectStorageObjectService;
import org.openstack4j.model.common.DLPayload;
import org.openstack4j.model.common.Payloads;
import org.openstack4j.model.storage.object.SwiftObject;
import org.openstack4j.model.storage.object.options.ObjectListOptions;
import org.openstack4j.model.storage.object.options.ObjectPutOptions;
import org.openstack4j.openstack.OSFactory;

public class Main {
	private static final String JMO_REPO = "jmo-repository"; 
	private static final String JMO_HOME = System.getProperty("user.dir");
	private static final String JMO_LOGS = "jmo-logs"; 

	public static void main(String[] args) {
		File cfg = new File (JMO_HOME + File.separator + ".credentials.properties");
		OSClient os = getOSclient(cfg);
		Scanner scan = new Scanner(System.in);
		String cmd = null;
		do{
			System.out.println("Please enter one of the following commands:\n"
					+ "config | upload-plugin | list | script-init | download | create-repo | help | quit\n");
			cmd = scan.next();
			
			switch(cmd){
			case "upload-plugin":
				String [] paths = scan.nextLine().split(" ");
				File plugin = new File(paths[1]);
				File script;
				if(paths.length == 3)
					script = new File (paths[2]);
				else
					script = null;
				uploadPlugin(os, plugin, script);
				break;
			case "create-repo":
				System.out.println("It may takes some time...");
				createRepo(os);
				break;
			case "list":
				listPlugins(os);
				break;
			case "script-init":
				ArrayList<String> plugins = new ArrayList<String>();
				Scanner scriptArgs = new Scanner(scan.nextLine());
				while(scriptArgs.hasNext()){
					String s = scriptArgs.next();
					plugins.add(s);
				}
				scriptArgs.close();
				prepareScriptInit(plugins, os, cfg);
				System.out.println("Script created!");
				break;
			case "download":
				String aux [] = scan.nextLine().split(" ");
				String dlArgs [] = new String [aux.length-1];
				for (int i = 0; i < dlArgs.length; i++){
					dlArgs[i] = aux[i+1];
				}
				List<String> logs = logsFilter(dlArgs, os);
				if (logs.size() != 0){
					File fLogs [] = downloadLogs(os, dlArgs[0], dlArgs[1], logs);
					viewLogs(fLogs, dlArgs[2], dlArgs[3]);
				}else{
					System.out.println("No logs found in the interval.");
				}
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
	 * Print the log records in the given interval.
	 * @param fLogs - Array of logs.
	 * @param date1 - Begin of the interval.
	 * @param date2 - End of the interval.
	 */
	private static void viewLogs(File[] fLogs, String date1, String date2) {
		boolean end = false;
		String datePattern ="^[0-9][0-9]-[0-9][0-9]-[0-9][0-9]_[0-9][0-9]:[0-9][0-9]:[0-9][0-9]";
		for (int i = 0; i < fLogs.length && end == false; i++){
			try {
				Scanner scan = new Scanner(fLogs[i]);
				while(scan.hasNextLine()){
					String recordDate = scan.next(datePattern);
					String aux = recordDate.substring(0, recordDate.length()-1);//Removing semi-column
					if(aux.compareTo(date1) >= 0 && aux.compareTo(date2) <= 0){
						System.out.println(recordDate);
						while(scan.hasNextLine() &&  !scan.hasNext(datePattern))
							System.out.println(scan.nextLine());
					}else{
						if(aux.compareTo(date2) == 1)
							end = true;
					}
				}
				scan.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}
	}
	/********************************************************************************************
	 * Download the logs in the logs list.
	 * @param os - The OpenStack Client.
	 * @param dlArgs - The download arguments.
	 * @param logs - The list of logs in Swift to download.
	 * @return The array of file downloaded.
	 */
	private static File[] downloadLogs(OSClient os, String instance, String plugin, List<String> logs) {
		Iterator<String> itr = logs.iterator();
		File [] fLogs = new File [logs.size()];
		int i = 0;
		while (itr.hasNext()){
			String log = itr.next();
			System.out.println("Downloading: " + '/' + JMO_LOGS + '/' + instance + "/logs/" + plugin + '/' + log);
			DLPayload download = os.objectStorage().objects().download(JMO_LOGS, instance + "/logs/" + plugin + '/' + log);
			BufferedReader br = new BufferedReader (new InputStreamReader(download.getInputStream()));
			fLogs[i] = new File(log);
			PrintWriter pw = null;
			try {
				pw = new PrintWriter(fLogs[i]);
				String logLine;
				while((logLine = br.readLine()) != null)
					pw.println(logLine);
			}catch(IOException e){
				e.printStackTrace();
			}
			pw.close();
			try {
				br.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			i++;
		}
		return fLogs;
	}
	/********************************************************************************************
	 * Return the logs in the given interval.
	 * @param dlArgs - The download arguments: hostname plugin date1 date2.
	 * @param os - The OpenStack client.
	 * @return The files to download
	 */
	private static List<String> logsFilter(String[] dlArgs, OSClient os) {
		String instance = dlArgs[0];
		String plugin = dlArgs[1];
		String date1 = dlArgs[2];
		String date2 = dlArgs[3];
		List<? extends SwiftObject> logsSwift = os.objectStorage().objects().list(JMO_LOGS, ObjectListOptions.create()
				.path(instance + '/' + "logs" + '/' + plugin));
		List<String> ris  = new ArrayList<String>();
		if(logsSwift.size() != 0){
			String sLogs [] = sortPathLogs(logsSwift);
			if(date1.compareTo(sLogs[sLogs.length-1].substring(0,14)) != 1 && date2.compareTo(sLogs[0].substring(0,14)) != -1 ){//some basic check
				boolean end = false;
				for (int i=0; i < sLogs.length && end == false; i++){
					if(sLogs[i].substring(0,14).compareTo(date1) >= 0){
						if(sLogs[i].substring(0,14).compareTo(date1) > 0 && (i-1) != -1)
							ris.add(sLogs[i-1]);
						else{
							ris.add(sLogs[i]);
							i++;
						}
						while (i < sLogs.length && sLogs[i].substring(0,14).compareTo(date2) <= 0){
							ris.add(sLogs[i]);
							i++;
						}
						end = true;
					}
				}
			}
		}else{
			System.out.println("No logs found for " + plugin + " plugin in " + instance);
		}
		return ris;
	}
	/********************************************************************************************
	 * Sort the logs by date returning an array of String.
	 * @param logsSwift - The list of objects.
	 * @return The logs sorted by date.
	 *  An array of Strings representing the logs sorted by date.
	 */
	private static String[] sortPathLogs(List<? extends SwiftObject> logsSwift) {
		String logs [] = new String [logsSwift.size()];
		Iterator<? extends SwiftObject> objIter = logsSwift.iterator();		
		for(int i=0; i < logs.length; i++){
			SwiftObject aux = objIter.next();
			String s[] = aux.getName().split("/");
			logs[i] = s[s.length-1];
		}
		Arrays.sort(logs);
		return logs;				
	}
	/********************************************************************************************
	 * Upload the given java class file to Swift and its optional scripts.
	 * @param os - The OpenStack client.
	 * @param files - A String array containing in location 0 the path of the java class file and 
	 * 				in the successive  eventually locations any scripts specified.
	 */
	private static void uploadPlugin(OSClient os, File plugin, File script) {
		if(!plugin.exists()){
			System.out.println("Error retriving the class file.");
		}else{
			os.objectStorage().objects().put(JMO_REPO, plugin.getName(),
					Payloads.create(plugin),
					ObjectPutOptions.create()
					.path("plugins"));
			if (script != null){
				if(!script.exists())
					System.out.println("Error retriving " + script.getName());
				else
					os.objectStorage().objects().put(JMO_REPO, script.getName(),
							Payloads.create(script),
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
		File files [] = dir.listFiles();
		for (int i=0; i < files.length; i++){
			swift.put(JMO_REPO, files[i].getName(),
					Payloads.create(files[i]),
					ObjectPutOptions.create()
					.path('/' + dir.getName()));
		}
	}
	/********************************************************************************************
	 * Creates a java properties file with the OpenStack credentials.
	 */
	private static void createConfig(File cfgFile) {
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		String aux = null;
		if (cfgFile.exists()){
			do{
			System.out.println("A config file already exists in " + cfgFile.getAbsolutePath() +". Do "
					+ "you want to overwrite it? (yes/no)");
			try {
				aux = br.readLine();
			} catch (IOException e) {
				e.printStackTrace();
			}
			}while (aux.compareTo("yes") != 0 && aux.compareTo("no") != 0);
		}
		if (aux == null || aux.compareTo("yes") == 0){
			cfgFile.getParentFile().mkdirs();
			OSCredentials cred = new OSCredentials();
			cred.askCredentials();
			writeConfig(cfgFile,cred);
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
	 * @param plugins - The plugins to add in the script.
	 * @param cfg - The JMO client configuration file.
	 */
	private static void prepareScriptInit(ArrayList<String> plugins, OSClient os, File cfg) {
		try {			
			ObjectStorageObjectService swift = os.objectStorage().objects();
			String plgPicking = "";
			for (String str : plugins){
				plgPicking += "downloadFile $JMO_CONTAINER/plugins/" + str + ".class ./plugins/" + str + ".class \n";
				List<? extends SwiftObject> script = swift.list(JMO_REPO, ObjectListOptions.create()
						.startsWith("scripts/" + str + ".sh"));
				if (script.size() != 0){
					plgPicking += "downloadFile $JMO_CONTAINER/scripts/" + str + ".sh ./scripts/" + str + ".sh \n";
				}
			}
			genetareInitScript(plgPicking,cfg);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	/********************************************************************************************
	 * Create the jmo-init.sh script from a template script.
	 * @param plgPicking - plugins section of the script.
	 * @param cfg - The JMO client configuration file.
	 * @throws IOException in case of an I/O error.
	 */
	private static void genetareInitScript(String plgPicking, File cfg) throws IOException {
		InputStream is = new FileInputStream(cfg);
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
		initScript.setExecutable(true, false);
	}
}