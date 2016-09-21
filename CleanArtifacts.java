package com.cts.CloudSet.cleanartifactory;

import hudson.Launcher;
import hudson.Extension;
import hudson.FilePath;
import hudson.util.FormValidation;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import jenkins.tasks.SimpleBuildStep;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;
import javax.servlet.ServletException;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**

 *
 * This plugin is used to clean artifactory. With this plugin, latest 5 releases or entire release artifacts are retained at any given point of time.
 * User can decide whether to keep latest 5 snapshots or clean completely.
 *
 * @author cognizant team
 */

public class CleanArtifacts extends Builder implements SimpleBuildStep {

    private  String artifactoryUrl;
    private String projectName;
    private  boolean keepSnapshots;
    private  boolean keepReleases;
    private String  userName="admin";
    private String password="password";
    

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public CleanArtifacts(String artifactoryUrl,String projectName,boolean keepSnapshots,boolean keepReleases) {

 
    	
        this.artifactoryUrl = artifactoryUrl;
        this.projectName=projectName;
        this.keepSnapshots=keepSnapshots;
        this.keepReleases=keepReleases;
        
        
    }

    public String getArtifactoryUrl() {
		return artifactoryUrl;
	}


	public String getProjectName() {
		return projectName;
	}



	public boolean isKeepSnapshots() {
		return keepSnapshots;
	}



	public boolean isKeepReleases() {
		return keepReleases;
	}



	public String getUserName() {
		return userName;
	}



	public String getPassword() {
		return password;
	}




    @Override
    public void perform(Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener) {
       
    	
    	listener.getLogger().println("Cleaning of Artifacts in artifactory "+artifactoryUrl + " for the project "+projectName+" is started");
    	//retrieve the buildnumbers for the given project
       getBuildNumbers(listener);
       //retrieve the snapshot info based on build number corresponding to the project
       getSnapshotsInfo(listener);
       //based on user input perform delete operation in artifactory
       keepordeletesnapshots(listener);
       
       listener.getLogger().println("Cleaning of Artifacts is completed");
       
 
    }
    
    /*this method is used to retrieve buildinfo of each build in a project using rest-api. based on the build info, 
     artifacts in snapshots and artifacts in release are seggregated. The build numbers of the snapshots artifacts are passed to the 
     keepordeletesnapshots method to perform delete operation. The release artifacts are deleted or retained based on user input.
    	*/
	

	public List<Integer> getSnapshotsInfo(TaskListener listener){

		List<Integer> buildNumbers=getBuildNumbers(listener);
		List<Integer> snapshots=new ArrayList<Integer>();
		List<Integer> releases=new ArrayList<Integer>();
		
		for(int i=0;i<buildNumbers.size();i++){
		
		URI uri=URI.create(artifactoryUrl+"/api/build/"+projectName+"/"+buildNumbers.get(i)+"");
		HttpHost host=new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
		CredentialsProvider credsProvider = new BasicCredentialsProvider();
		credsProvider.setCredentials(new AuthScope(uri.getHost(), uri.getPort()),new UsernamePasswordCredentials(userName, password));
		AuthCache authCache=new BasicAuthCache();
		BasicScheme basicAuth = new BasicScheme();
		authCache.put(host, basicAuth);
		CloseableHttpClient httpClient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();
		HttpGet httpGet=new HttpGet(uri);
		HttpClientContext localContext = HttpClientContext.create();
		localContext.setAuthCache(authCache);
		HttpResponse response;
		try {
			response = httpClient.execute(host, httpGet, localContext);
			
			String obj=EntityUtils.toString(response.getEntity());
			
			JSONParser jsonParser=new JSONParser();
			
			Object object=jsonParser.parse(obj);
			
			JSONObject jsonObject=(JSONObject) object;
			
			String buildInfo=jsonObject.get("buildInfo").toString();
			
			Object object1=jsonParser.parse(buildInfo);
			
			JSONObject jsonObject1=(JSONObject) object1;		
			
			JSONArray array=(JSONArray) jsonObject1.get("statuses");

			
	if(array==null){
				snapshots.add(buildNumbers.get(i));
				
	}else{
		listener.getLogger().println("Release BuildNumbers :"+buildNumbers.get(i));
		releases.add(buildNumbers.get(i));
		
		if(keepReleases && releases.size()>5){
			for(int j=5;j<releases.size();j++){
				deleteBuilds(releases.get(j),listener);
			}
		}else
		{
			listener.getLogger().println("We have only "+releases.size()+"  release artifact. nothing to delete ");
		}
		
		
	}
		} catch (ClientProtocolException e) {
			
			e.printStackTrace();
		} catch (IOException e) {
			
			e.printStackTrace();
		} catch (ParseException e) {
			
			e.printStackTrace();
		}
		
		}
		return snapshots;
	}
	
	/*this method is used to retrieve buildnumbers of the project using rest-api. this will return the list of build numbers in reverse 
	order, which will be used in getSnapshotsInfo(TaskListener listener)*/
	
public List<Integer> getBuildNumbers(TaskListener listener){
		
		List<Integer> listInt = null;
		String buildNumbers="";
		URI uri=URI.create(artifactoryUrl+"/api/build/"+projectName+"");
		HttpHost host=new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
		CredentialsProvider credsProvider = new BasicCredentialsProvider();
		credsProvider.setCredentials(new AuthScope(uri.getHost(), uri.getPort()),new UsernamePasswordCredentials(userName, password));
		AuthCache authCache=new BasicAuthCache();
		BasicScheme basicAuth = new BasicScheme();
		authCache.put(host, basicAuth);
		CloseableHttpClient httpClient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();
		HttpGet httpGet=new HttpGet(uri);
		HttpClientContext localContext = HttpClientContext.create();
		localContext.setAuthCache(authCache);
		HttpResponse response;
	
			try {
				response = httpClient.execute(host, httpGet, localContext);
				buildNumbers=EntityUtils.toString(response.getEntity());	
			 	listInt = new ArrayList<Integer>();
			 	JSONParser jsonParser=new JSONParser();
				Object jsonObject= jsonParser.parse(buildNumbers);
				org.json.simple.JSONObject object=(org.json.simple.JSONObject) jsonObject;
				JSONArray jsonArray=(JSONArray)object.get("buildsNumbers");
				@SuppressWarnings("rawtypes")
				Iterator iterator=jsonArray.iterator();

				while(iterator.hasNext()) {
					object=(org.json.simple.JSONObject) iterator.next();
					listInt.add(Integer.valueOf(((String) object.get("uri")).substring(1)));
				}
				
				Collections.sort(listInt, Collections.reverseOrder());
				
			} catch (ClientProtocolException e) {
				
				e.printStackTrace();
			} catch (IOException e) {
				
				e.printStackTrace();
			} catch (ParseException e) {
				
				e.printStackTrace();
			}
			
	listener.getLogger().println("Project Build Numbers : "+listInt);	
	
		return listInt;
	}

/*this methos is used to check the snapshot size and call delete method to delete snapshots*/

public void keepordeletesnapshots(TaskListener listener){
	List<Integer> snapshots=getSnapshotsInfo(listener);
   
		if(keepSnapshots && snapshots.size()>5){
		    for(int i=5;i<snapshots.size();i++){
		 
		    	deleteBuilds(snapshots.get(i),listener);
		    }
		}else if(!keepSnapshots){
			for(int j=0;j<snapshots.size();j++){
			
				deleteBuilds(snapshots.get(j),listener);
			}
		}else{
			listener.getLogger().println("You have only "+snapshots.size()+" snapshots in the artifactory");
		}
	
}

/*this methos uses delete api to delete snapshot artifacts based on inputs from keepordeletesnapshots*/
public void deleteBuilds(int buildNumber,TaskListener listener){

	URI uri=URI.create(artifactoryUrl+"/api/build/"+projectName+"?buildNumbers="+buildNumber+"&artifacts=1");
	HttpHost host=new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
	CredentialsProvider credsProvider = new BasicCredentialsProvider();
	credsProvider.setCredentials(new AuthScope(uri.getHost(), uri.getPort()),new UsernamePasswordCredentials(userName, password));
	AuthCache authCache=new BasicAuthCache();
	BasicScheme basicAuth = new BasicScheme();
	authCache.put(host, basicAuth);
	CloseableHttpClient httpClient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();	
	HttpDelete httpDelete = new HttpDelete(uri);
	// Add AuthCache to the execution context
	HttpClientContext localContext = HttpClientContext.create();
	localContext.setAuthCache(authCache);

	try {
		HttpResponse response = httpClient.execute(host, httpDelete, localContext);

		listener.getLogger().println(EntityUtils.toString(response.getEntity()));

	} catch (ClientProtocolException e) {

		e.printStackTrace();
	} catch (IOException e) {

		e.printStackTrace();
	}


}


    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

   
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {



        /**
         * In order to load the persisted global configuration, you have to 
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        /**
         * Performs on-the-fly validation of the form field 'name'.
         *
         * @param value
         *      This parameter receives the value that the user has typed.
         * @return
         *      Indicates the outcome of the validation. This is sent to the browser.
         *      <p>
         *      Note that returning {@link FormValidation#error(String)} does not
         *      prevent the form from being saved. It just means that a message
         *      will be displayed to the user. 
         */
        public FormValidation doCheckArtifactoryUrl(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Artifactory URL cannot be empty");
            return FormValidation.ok();
        }
        
        public FormValidation doCheckProjectName(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Project Name cannot be empty");
            return FormValidation.ok();
        }
        
        
        

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "CloudSet Clean Artifactory";
        }

        public boolean configure(StaplerRequest req, net.sf.json.JSONObject formData) throws FormException {
            // To persist global configuration information,
            // set that to properties and call save().
          
            // ^Can also use req.bindJSON(this, formData);
            //  (easier when there are many fields; need set* methods for this, like setUseFrench)
            save();
            return super.configure(req,formData);
        }

        /**
         * This method returns true if the global configuration says we should speak French.
         *
         * The method name is bit awkward because global.jelly calls this method to determine
         * the initial state of the checkbox by the naming convention.
         */
       
    }
}

