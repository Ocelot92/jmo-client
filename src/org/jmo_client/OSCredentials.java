package org.jmo_client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class OSCredentials {
	private String user, password, tenant, keystoneEndpoint, swiftEndpoint;
	
	public OSCredentials() {
		user = password = tenant = keystoneEndpoint = swiftEndpoint = null;
	}
	
	/**
	 * Ask the user for type the OpenStack Credentials and save them in this
	 * object.
	 */
	public void askCredentials (){
		try{
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		System.out.println("Enter the username:");
		user = br.readLine();
		System.out.println("Enter the password:");
		password = br.readLine();
		System.out.println("Enter the tenant:");
		tenant = br.readLine();
		System.out.println("Enter the Keystone endpoint (ex. http://192.168.1.1:5000/v2.0):");
		keystoneEndpoint = br.readLine();
		System.out.println("Enter a Swift endpoint reachable from the instances \n(ex. http://192.168.1.1:8080/v1/AUTH_'tenantID'):");
		swiftEndpoint = br.readLine();
		}catch (IOException e){
			e.printStackTrace();
		}
	}
	
	@Override
	public String toString(){
		String ret = "user: " + user + '\n'
				+ "password: " + password + '\n'
				+ "tenant: " + tenant + '\n'
				+ "Keystone endpoint: " + keystoneEndpoint + '\n'
				+ "Swift endpoint: " + swiftEndpoint + '\n';
		return ret;
	}
	
	/**
	 * @return the user
	 */
	public String getUser() {
		return user;
	}

	/**
	 * @param user the user to set
	 */
	public void setUser(String user) {
		this.user = user;
	}

	/**
	 * @return the password
	 */
	public String getPassword() {
		return password;
	}

	/**
	 * @param password the password to set
	 */
	public void setPassword(String password) {
		this.password = password;
	}

	/**
	 * @return the tenant
	 */
	public String getTenant() {
		return tenant;
	}

	/**
	 * @param tenant the tenant to set
	 */
	public void setTenant(String tenant) {
		this.tenant = tenant;
	}

	/**
	 * @return the keystoneEndpoint
	 */
	public String getKeystoneEndpoint() {
		return keystoneEndpoint;
	}

	/**
	 * @param keystoneEndpoint the keystoneEndpoint to set
	 */
	public void setKeystoneEndpoint(String keystoneEndpoint) {
		this.keystoneEndpoint = keystoneEndpoint;
	}
	
	/**
	 * @return the swiftEndpoint
	 */
	public String getSwiftEndpoint() {
		return swiftEndpoint;
	}
	
	/**
	 * @param swiftEndpoint the swiftEndpoint to set
	 */
	public void setSwiftEndpoint(String swiftEndpoint) {
		this.swiftEndpoint = swiftEndpoint;
	}
	
}
