# ADS File Submission App

This is sample application that can upload file to IZGateway ADS Restful API. It includes two ways to do the submission.

# Java Program with Apache HttpClient

* Clone the Project to your local. 
* Run maven install

      	mvn clean install


* Create a input json file with inputs such as below,which will sent as argument to the java program.
	
	 	{
	     	"ClientCertificatePFXPath" :"path to pfx cert file",
		  	"ClientCertificatePFXPassphrase":"passphrase for pfx file",
			"FacilityId": "",
			"DestinationId": "",
			"ReportType": "",
			"UploadFilePath": ""
			"Period": ""
	
		}
	
* Run the program 
	
        java -jar target/ads-file-submission-app-0.0.1-SNAPSHOT.jar "{path to inputs.json file}"

# HTML Post Form 

* Open fileSubmissionForm.html in the browser and submit the form.

  
