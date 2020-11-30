package com.jotne.epm.trueplm.client;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import com.jotne.demo.ApiException;
import com.jotne.demo.api.AdminControllerApi;
import com.jotne.demo.api.AuthorizationControllerApi;
import com.jotne.demo.api.BreakdownControllerApi;
import com.jotne.demo.api.DataControllerApi;
import com.jotne.demo.api.ExchangeControllerApi;
import com.jotne.demo.model.AggregatedProperty;
import com.jotne.demo.model.BreakdownElementInfo;
import com.jotne.demo.model.BreakdownElementSearchResultInfo;
import com.jotne.demo.model.ByteArrayResource;
import com.jotne.demo.model.DataFileInfo;
import com.jotne.demo.model.DataFileInfoWrapper;
import com.jotne.demo.model.DataFileSearchResultInfo;
import com.jotne.demo.model.FileInfo;
import com.jotne.demo.model.LoginInfo;
import com.jotne.demo.model.LoginRez;
import com.jotne.demo.model.ProjectInfo;
import com.jotne.demo.model.UsersProjectInfo;
import com.squareup.okhttp.OkHttpClient;

public class Main {

	static final String loginNane = "azo@jotne.com";
	static final String group = "sdai-group"; // All TruePLM users belong to the same group.
	
	static final String PROJECT_REPO = "TruePLMprojectsRep";
	
	public static void main(String[] args) {
		try {
			new Main().run();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	void run() throws ApiException, FileNotFoundException, IOException, ParseException {
		
		Properties prop = new Properties();
		InputStream is = getClass().getClassLoader().getResourceAsStream("config.properties");
		prop.load(is);
		// Get password from configuration file
		// Note, actual password is NOT stored in Git
		String password = prop.getProperty("password");
		String token = login(loginNane, group, password);
		
		// Get user meta-data: name, e-mail, ...
		LoginInfo userInfo = getUser(token, loginNane);
		// Type (role) user has in its project
		String userType = userInfo.getUserProjects().get(0).getUserRegisteredAs().get(0);
		
		// Get meta-data on projects available for the user
		List<UsersProjectInfo> projects = getProjectsForUser(token);
		if (projects == null || projects.size() == 0)
			throw new ApiException("No projects found");
		
		// Assume use has access to one single project
		ProjectInfo projInfo = projects.get(0).getInProject();
		
		// Search for breakdown element named "D00 / ASD/AIA Bike"
		// and list its children elements and documents attached to it
		BreakdownElementSearchResultInfo element =
				searchForBreakdownElements(token, "D00 / ASD/AIA Bike", projInfo.getProjectModelId(), PROJECT_REPO, userType);
		
		// Search documents and print out meta-data for some of them
		List<DataFileSearchResultInfo> docs =
				searchForDocuments(token, projInfo.getProjectModelId(), userType);
		if (docs != null && docs.size() > 1) {
			// download the second one
			DataFileSearchResultInfo doc = docs.get(1);
			downloadDocument(token, PROJECT_REPO, projInfo.getProjectModelId(), doc, userType);
		}
		
		// Upload new document to the found breakdown element
		File fileToUpload = new File("../README.md");
		uploadDocument(token, PROJECT_REPO, projInfo.getProjectModelId(), element.getBkdnElemInfo().getInstanceId(), fileToUpload, userType);
		
		element = searchForSensorDataContainer(token, projInfo.getProjectModelId(), PROJECT_REPO, userType);
		retrieveSensorData(token, PROJECT_REPO, projInfo.getProjectModelId(), element.getBkdnElemInfo().getInstanceId());
		
		exportProjectAsDEXPackage(token, "TruePLMprojectsRep", projInfo.getProjectModelId(), "Bike.zip");
						
		logout(token);
	}
	
	/*
	 * Receives token for any other services during a web session
	 * It is nice to close (logout) given token when it is not needed
	 */
	String login(String user, String group, String password) throws ApiException {
		AuthorizationControllerApi api = new AuthorizationControllerApi();
		// Normally web server (Tomcat, etc) and EDM server are on the same host
		// That is why "localhost" is hardcoded here.
		// However we may use any publicity available EDM server to connect to 
		LoginRez res = api.loginSubmitUsingPOST(null, group, password, "9090", "localhost", user);
		
		if (res.getError() != null)
			throw new ApiException("Login error");
		return res.getToken();
	}
	
	/*
	 * Closes given token
	 * Any exception is swallowed here. And this looks fine
	 */
	void logout(String token) {
		AuthorizationControllerApi api = new AuthorizationControllerApi();
		try {
			api.logoutUsingDELETE(token);
		} catch (ApiException e) {
			// Nothing do go here. Just skip any exception
		}
	}
	
	LoginInfo getUser(String token, String user) throws ApiException {
		AdminControllerApi api = new AdminControllerApi();
		List<LoginInfo> res = api.getUserInfoUsingGET(token);
		LoginInfo usr = res.get(0); 
		
		System.out.print("User:\t");
		System.out.println(usr.getUserName());
		System.out.print("Nnme:\t");
		System.out.println(usr.getRealName());
		System.out.print("E-mail:\t");
		System.out.println(usr.getUserEmail());
		return usr; // There should be only one element
	}
	
	List<UsersProjectInfo> getProjectsForUser(String token) throws ApiException {
		AdminControllerApi api = new AdminControllerApi();
		List<UsersProjectInfo> res = api.getUserProjectsUsingGET(token);
		
		if (res != null) {
			System.out.println("\nAvailable projects:");
			for (UsersProjectInfo p : res) {
				System.out.print("Name: ");
				System.out.println(p.getInProject().getName());
				System.out.print("Description: ");
				System.out.println(p.getInProject().getProjectDescr());
			}
		}
		
		return res;
	}
	
	List<DataFileSearchResultInfo> searchForDocuments(String token, String modelName, String userType) throws ApiException {
		DataControllerApi api = new DataControllerApi();
		String descriptionPattern = "*"; // means any description
		String titlePattern = "*"; // means any description
		
		String editor = null; // To find documents edited my current user
			
		List<DataFileSearchResultInfo> docs = api.searchDocumentsUsingGET(
				modelName, PROJECT_REPO, token, userType, null, descriptionPattern, null, null, editor, null, null, null, null, titlePattern);
		
		if (docs == null || docs.size() == 0) {
			System.out.println("No documents found");
		}
		else {
			// print out metadata of not more than certain number of documents
			int index = 1; // number of found document: 1 .. n 
			int maxItems = 3; // maximum items to be printed out
			System.out.println("\nFollowing document are found");
			for(DataFileSearchResultInfo doc : docs) {
				System.out.print("#");
				System.out.print(index);
				System.out.print(": ");
		    	printDocument(doc);
			    if (index++ > maxItems) {
			    	System.out.println("... more...");
			    	break;
			    }
			}
		}
		
		return docs;
	}
	
	void downloadDocument(String token, String repoName, String modelName, DataFileSearchResultInfo doc, String userType) throws ApiException, FileNotFoundException, IOException {
		DataControllerApi api = new DataControllerApi();
		FileInfo info = api.getFileVerLinkUsingGET(modelName, "filename", repoName, token, userType, doc.getInstanceId());
		
		// Download file using "low level" way
		// There is a bug in Swagger code that prevents using "nice looking" code
		String fileUrl = api.getApiClient().getBasePath() + "/api/dat/file/data/" + info.getSource() + "/filename/" + token;
		try (BufferedInputStream in = new BufferedInputStream(new URL(fileUrl).openStream());
				FileOutputStream fileOutputStream = new FileOutputStream(doc.getFileName())) {
				byte dataBuffer[] = new byte[1024];
				int bytesRead;
				while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
					fileOutputStream.write(dataBuffer, 0, bytesRead);
				}
		} catch (IOException e) {
			// handle exception
		}
		System.out.println("\nDownload completed");
	}
	
	void uploadDocument(String token, String repoName, String modelName, Long targetNode, File file, String userType) throws ApiException, FileNotFoundException, IOException {
		DataControllerApi api = new DataControllerApi();
		Boolean newIssue = Boolean.FALSE;
		Long actTimestamp = 0l;
		DataFileInfoWrapper df = api.addFileUsingPOST(file, modelName, targetNode, repoName, token, userType,
				actTimestamp, "man", "urn:rdl:epm-std:CAD_file__STEP_AP214_", "file uploaded during KYKLOS workshop", "urn:rdl:epm-std:AOCS", "man", newIssue,
				"urn:rdl:epm-std:0", "man", "man", "man", "urn:rdl:epm-std:Internal (ESA)", "urn:rdl:epm-std:Approved", file.getName());
				
		System.out.println("\nUpload completed");
		System.out.println("Timestamp: " + df.getUpdatedNodeDate());
	}

	void printDocument(DataFileSearchResultInfo obj) {
		System.out.println(obj.getTitle());
		System.out.print("Type: ");
		System.out.println(obj.getDataType());
		System.out.print("Version: ");
		System.out.println(obj.getLastVersionId());
	}
	
	BreakdownElementSearchResultInfo searchForBreakdownElements(String token, String namePattern, String modelName, String repoName, String userType) throws ApiException {
		BreakdownControllerApi api = new BreakdownControllerApi();
		long limit = 10;
		long parentNodeID = 0; // search with no parent specified
		
		// First we need to find parent element for all parts
		// The element named "Bike system"
		List<BreakdownElementSearchResultInfo> res = api.advancedSearchNodeUsingGET(modelName, repoName, token, userType, null, null, null, "*",
				null, null, null, limit, parentNodeID, null, null, "Bike system", null, null, null);
		
		// There should be one single element found
		parentNodeID = res.get(0).getBkdnElemInfo().getInstanceId();

		// Now search for required element inside specified parent
		res = api.advancedSearchNodeUsingGET(modelName, repoName, token, userType, null, null, null, "*",
				null, null, null, limit, parentNodeID, null, null, namePattern, null, null, null);
		
		System.out.println("\nBreakdown elemets found:");
		for (BreakdownElementSearchResultInfo el : res) {
			System.out.print("ID: ");
			System.out.println(el.getBkdnElemInfo().getId());
			System.out.print("Name: ");
			System.out.println(el.getBkdnElemInfo().getName());
			System.out.print("Type: ");
			System.out.println(el.getBkdnElemInfo().getElementType());
		}
		
		List<BreakdownElementInfo> children = res.get(0).getBkdnElemInfo().getChildren();
		if (children != null) {
			System.out.print("\nChildren elements: ");
			System.out.println(children.size());
			int idx = 0;
			for (BreakdownElementInfo el : children) {
				System.out.print("#");
				System.out.println(++idx);
				System.out.print("ID: ");
				System.out.println(el.getId());
				System.out.print("Name: ");
				System.out.println(el.getName());
				System.out.print("Type: ");
				System.out.println(el.getElementType());
			}
		}
		
		List<DataFileInfo> documents = res.get(0).getBkdnElemInfo().getDataContents();
		if (documents != null ) {
			System.out.print("\nDocuments: ");
			System.out.println(documents.size());
			int idx = 0;
			for (DataFileInfo dat : documents) {
				System.out.print("#");
				System.out.println(++idx);
				System.out.print("ID: ");
				System.out.println(dat.getId());
				System.out.print("Title: ");
				System.out.println(dat.getTitle());
				System.out.print("Type: ");
				System.out.println(dat.getDataType());
				System.out.print("Updated by: ");
				System.out.println(dat.getModifiedByUser());			
			}
		}
		return res.get(0);
	}

	BreakdownElementSearchResultInfo searchForSensorDataContainer(String token, String modelName, String repoName, String userType) throws ApiException {
		BreakdownControllerApi api = new BreakdownControllerApi();
		long limit = 10;
		long nodeID = 0;
		String propertyName = "urn:rdl:Bike:serial number";
		String propertyValue = "13483027";
		List<BreakdownElementSearchResultInfo> res = api.advancedSearchNodeUsingGET(modelName, repoName, token, userType, null, null, null, "*",
				null, null, null, limit, nodeID, null, null, "*", Arrays.asList(propertyName), Arrays.asList(propertyValue), null);
		
		System.out.println("\nBreakdown elemets found:");
		for (BreakdownElementSearchResultInfo el : res) {
			System.out.print("ID: ");
			System.out.println(el.getBkdnElemInfo().getId());
			System.out.print("Name: ");
			System.out.println(el.getBkdnElemInfo().getName());
			System.out.print("Type: ");
			System.out.println(el.getBkdnElemInfo().getElementType());
		}
		return res.get(0);
	}
	
	void retrieveSensorData(String token, String repoName, String modelName, long nodeId) throws ApiException, ParseException {
		BreakdownControllerApi api = new BreakdownControllerApi();
		
		// Quite tricky way to get Unix timestamp from human readable time format 
		Date fromDate = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").parse("2020/04/27 00:00:00");
		String from = fromDate.getTime() / 1000 + "";
		String to = null;
		
		long page = 1; // first page
		long pageSize = 3; // take just few rows
		String propertyUri = "urn:rdl:Bike:point list";
		AggregatedProperty aggrProp = api.getAggrPropUsingGET(modelName, nodeId, propertyUri, repoName, token, from, page, pageSize, to);
		
		System.out.println("\nSensor data:");
		for (String val : aggrProp.getValues()) {
			System.out.println(val);
		}
	}
	
	void exportProjectAsDEXPackage(String token, String repoName, String modelName, String filePath) throws ApiException, FileNotFoundException, IOException {
		ExchangeControllerApi api = new ExchangeControllerApi();
		
		// Export may take long.
		// We use syncronious requests. So we need to increase timeout values
		OkHttpClient httpClient = api.getApiClient().getHttpClient();
		httpClient.setConnectTimeout(600, TimeUnit.SECONDS);
		httpClient.setReadTimeout(600, TimeUnit.SECONDS);
		httpClient.setWriteTimeout(600, TimeUnit.SECONDS);

		FileInfo info = api.exportProjectToFileUsingGET(modelName, modelName, repoName, token);
		
		DataControllerApi dat = new DataControllerApi();
		ByteArrayResource arr = dat.getFileDataUsingGET("title", info.getSource(), token);
		
		try (FileOutputStream fos = new FileOutputStream(filePath)) {
			fos.write(arr.getByteArray());
			//fos.close(); There is no more need for this line since you had created the instance of "fos" inside the try. And this will automatically close the OutputStream
		}
		
		System.out.println("\nExport completed");
	}

}
