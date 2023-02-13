import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Properties;
import javax.net.ssl.SSLContext;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class FileSubmissionApplication {

	public static void main(String[] args) throws NoSuchAlgorithmException, KeyStoreException, CertificateException,
			UnrecoverableKeyException, KeyManagementException {

		JSONParser jsonParser = new JSONParser();
		Properties properties = new Properties();
		try {
			Object obj = jsonParser.parse(new FileReader(args[0]));
			JSONObject jsonObject = (JSONObject) obj;
			String clientCertPath = (String) jsonObject.get("ClientCertificatePFXPath");
			String keyPassphrase = (String) jsonObject.get("ClientCertificatePFXPassphrase");
			InputStream truststoreStream = FileSubmissionApplication.class.getClassLoader()
					.getResourceAsStream("ssl/adsTrustStore.jks");
			properties.load(
					FileSubmissionApplication.class.getClassLoader().getResourceAsStream("application.properties"));
			String truststorePassphrase = properties.getProperty("truststore.passphrase");
			String adsRestApi = properties.getProperty("ads.rest.api");

			// ssl context to connect to https
			SSLConnectionSocketFactory sslConFactory = sslUtil(clientCertPath, keyPassphrase, truststoreStream,
					truststorePassphrase);

			// post request
			String response = multipartPostRequest(adsRestApi, sslConFactory, jsonObject);
			System.out.println(response);
		} catch (IOException | ParseException e) {
			e.printStackTrace();
		}
	}

	private static SSLConnectionSocketFactory sslUtil(String clientCertPath, String keystorePassphrase,
			InputStream truststoreStream, String truststorePassphrase)
			throws NoSuchAlgorithmException, CertificateException, IOException,
			KeyManagementException, UnrecoverableKeyException, KeyStoreException {

		File pfxFile = new File(clientCertPath);
		// Loading the keystore with pfx file
		KeyStore keyStore = KeyStore.getInstance("PKCS12");
		FileInputStream keystoreStream = new FileInputStream(pfxFile);
		keyStore.load(keystoreStream, keystorePassphrase.strip().toCharArray());

		// Loading the truststore
		KeyStore truststore = KeyStore.getInstance("JKS");
		truststore.load(truststoreStream, truststorePassphrase.strip().toCharArray());

		// Create SSL Context with keystore and truststore
		SSLContext sslContext = new SSLContextBuilder().loadTrustMaterial(truststore, new TrustSelfSignedStrategy())
				.loadKeyMaterial(keyStore, keystorePassphrase.strip().toCharArray()).build();

		// Create socket factory with ssl context (Note:hostname verification is
		// disabled for local testing)
		SSLConnectionSocketFactory sslConFactory = new SSLConnectionSocketFactory(sslContext,
				new NoopHostnameVerifier());
		truststoreStream.close();
		keystoreStream.close();

		return sslConFactory;

	}

	private static String multipartPostRequest(String adsRestApi, SSLConnectionSocketFactory sslConFactory,
			JSONObject jsonObject) {

		String response = null;
        String filePath = (String) jsonObject.get("UploadFilePath");
        String destinationId = (String) jsonObject.get("DestinationId");
        String period = (String)jsonObject.get("Period");
        File file = new File(filePath);
        String facilityId = (String) jsonObject.get("FacilityId");
        String reportType = (String) jsonObject.get("ReportType");
        String fileName = file.getName();

        System.out.println("Inputs : \nFacility Id:" + facilityId + "\nDestination Id: " + destinationId
                + "\nPeriod:" + period + "\nReport Type: " + reportType + "\nFile Name: " + fileName + "\nFile Location:"
                + filePath);
		try(        
		    FileInputStream fileStream = new FileInputStream(file);
            CloseableHttpClient httpClient = HttpClients.custom().setSSLSocketFactory(sslConFactory).build();
		) {

			HttpUriRequest multipartRequest = 
			    buildRequest(adsRestApi, facilityId, destinationId, period, reportType, fileName, fileStream);

			System.out.println("Processing request " + multipartRequest.getRequestLine() + "... please wait");

			// Executing the request
			HttpResponse httpResponse = httpClient.execute(multipartRequest);
			response = handleResponse(httpResponse);

		} catch (IOException e) {
			e.printStackTrace();
		}
		return response;
	}

	private static HttpUriRequest buildRequest(String adsRestApi, String facilityId, String destinationId, 
	    String period, String reportType, String fileName, FileInputStream fileStream
	) {

		// build multipart entity with inputs
		MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
		entityBuilder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
		entityBuilder.setContentType(ContentType.MULTIPART_FORM_DATA);
		entityBuilder.addTextBody("facilityId", facilityId, ContentType.TEXT_PLAIN);
		entityBuilder.addTextBody("reportType", reportType, ContentType.TEXT_PLAIN);
		entityBuilder.addTextBody("period", period, ContentType.TEXT_PLAIN);
		entityBuilder.addTextBody("filename", fileName, ContentType.TEXT_PLAIN);
		entityBuilder.addBinaryBody("file", fileStream, getContentType(fileName), fileName);

		HttpEntity entity = entityBuilder.build();
		RequestBuilder reqbuilder = RequestBuilder.post(adsRestApi + destinationId);

		// Set the entity object to the RequestBuilder
		reqbuilder.setEntity(entity);

		// Building the request

		return reqbuilder.build();
	}

	private static ContentType getContentType(String fileName) {
        if (fileName.endsWith(".zip")) {
            return ContentType.create("application/zip");
        } else if (fileName.endsWith(".xslx")) {
            return ContentType.create("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        } else if (fileName.endsWith(".xsl")) {
            return ContentType.create("application/vnd.ms-excel");
        }
        return ContentType.create("application/zip");
    }

    public static String handleResponse(HttpResponse response) throws IOException {

		int status = response.getStatusLine().getStatusCode();
		HttpEntity entityResp = response.getEntity();

		if (status >= 200 && status < 300) {
			return entityResp != null ? "Success Response code: " + status + "\n"+EntityUtils.toString(entityResp) : null;

		} else {
			return ("Error Response code: " + status + "\n"+EntityUtils.toString(entityResp));
		}

	}
}