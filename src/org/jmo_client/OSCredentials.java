package org.jmo_client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class OSCredentials {
	private String user, password, tenant, keystoneEndpoint;
	public OSCredentials() {
		user = password = tenant = keystoneEndpoint = null;
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
		System.out.println("Enter the keystone endpoint (ex. http://192.168.1.1:5000/v2.0):");
		keystoneEndpoint = br.readLine();
		}catch (IOException e){
			e.printStackTrace();
		}
	}
	@Override
	public String toString(){
		String ret = "user: " + user + '\n'
				+ "password: " + password + '\n'
				+ "tenant: " + tenant + '\n'
				+ "keystone endpoint: " + keystoneEndpoint + '\n';
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


	
}
