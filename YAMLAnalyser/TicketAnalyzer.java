// Import Statements
package YAMLAnalyser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.http.*;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.html.HTML;

import org.joda.time.DateTime;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.yaml.snakeyaml.Yaml;

public class TicketAnalyzer {

	// Constants
	public static final String DOMAIN = "domain";
	public static final String INPUT_FILE_POSTFIX = ".yaml";
	public static final String OUTPUT_POSTFIX_CUSTOMER = "_customer.txt";
	public static final String OUTPUT_POSTFIX_ENGINEER = "_engineer.txt";
	// ** Note below is hard-coded in some places - not sure benefit of using a
	// constant here anyway
	public static final String ENGINEER_FLAG = "*SUPPORT ENGINEER ONLY*";
	public static final String CUSTOMER_UPDATE = "<p>Hello,</p><br><p>Our Security scan of your .yaml file shows that you may have secrets that need to be rotated. Please review the results in each <b>_customer.txt</b> file uploaded above and let us know once the secrets have been rotated.</p>";
	public static final String ENGINEER_UPDATE = "<p>Support Engineer,</p><br><p>Please respond to queries from customer and work with them to rotate passwords in <b>_engineer.txt</b> file(s) above which contains non customer-rotatable secrets.</p>";

	public static void main(String[] args) throws URISyntaxException, ParseException, InterruptedException {
		// Declare variable(s)
		BufferedReader br;
		BufferedWriter bwCustomer;
		BufferedWriter bwEngineer;
		List<String> ticketIDs = new ArrayList<String>();
		int customerDiagsCounter = 0;

		// Add tickets to ArrayList
//		ticketIDs.add("2035327"); // Test ticket

		for (String ticketID : ticketIDs) {

			// Download diags + move file to project root
			if (downloadDiags(ticketID) == 0) {
				try {
					// Create Folder object and save reference in File variable
					File folder = new File(ticketID);

					// Get an array of all files in the folder
					File[] files = folder.listFiles();

					// For each .yaml file in the ticket directory
					for (File file : files) {

						// Get index of last '.' so we can remove .yaml extension
						int indexDot = file.getName().lastIndexOf(".");

						// Amended file name
						String fileName = file.getName().substring(0, indexDot);

						// Buffered Reader object for file
						br = getBufferedReader(file.getAbsolutePath());

						// Create two Buffered Writer objects 1) Customer file(s) and 2) Engineer
						// file(s)
						bwCustomer = getBufferedWriter(
								String.format("%s/%s%s", ticketID, fileName, OUTPUT_POSTFIX_CUSTOMER));
						bwEngineer = getBufferedWriter(
								String.format("%s/%s%s", ticketID, fileName, OUTPUT_POSTFIX_ENGINEER));

						// Analyse our yaml file
						analyseYaml(file, br, bwCustomer, bwEngineer, ticketID);

						// Close resources so we can move the file
						br.close();
						bwCustomer.close();
						bwEngineer.close();

						// Move processed diags from project root to upload directory
						if (moveDiags(ticketID) == 0) {
							System.out.println("Diags moved succesfully for " + ticketID);
						} else {
							System.out.printf("Error moving %s for %s%n", fileName, ticketID);
							// Error moving file, so skip this file
							continue;
						}

						// Upload customer diags to ticket
						// Note we need to do this for each file since Syncsafely only allows one public
						// file per time
						if (uploadCustomerDiags(ticketID, fileName) == 0) {
							// Increment customerDiagsCounter
							customerDiagsCounter++;
						}
					}

					if (customerDiagsCounter != 0) {
						// Only send customer-facing update if we have uploaded at least one file
						fdAPI(ticketID, CUSTOMER_UPDATE, false);
					}

					// Upload engineer diags as a private note
					// Note we can upload multiple files at once if it's a private note
					if (uploadEngineerDiags(ticketID) == 0) {
						// Engineers diags have been uploaded successfully, so add a private note asking
						// engineer to review
						fdAPI(ticketID, ENGINEER_UPDATE, true);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				// Error downloading diags for ticket, so continue onto next one
				System.out.println("Error downloading diags for " + ticketID);
				continue;
			}
		}
	}

	public static BufferedReader getBufferedReader(String fileName) throws FileNotFoundException {
		// Create File object
		File file = new File(fileName);

		// Create FileReaderObject
		FileReader fr = new FileReader(file);

		// Create BufferedReader object
		BufferedReader br = new BufferedReader(fr);

		return br;
	}

	public static BufferedWriter getBufferedWriter(String fileName) throws IOException {
		// Create File object
		File file = new File(fileName);

		// Create FileWriter Object
		FileWriter fw = new FileWriter(file);

		// Create BufferedWriter Object
		BufferedWriter bw = new BufferedWriter(fw);

		return bw;
	}

	public static int downloadDiags(String ticketNumber) {
		// Command line string that does below:
		// 1) Downloads diags with .yaml extension from ticket into
		// /mnt/c/work/diags/%s/
		// 2) Create ticket directory in project root folder
		// 3) Moves .yaml files from /mnt/c/work/diags/%s/ into
		// /mnt/c/Users/RoryHandley/eclipse-workspace-work/YAMLAnalyser/%s so we can
		// process in this script.
		String fullCommand = String.format(
				"SyncSafely.sh -n %s -f %s && mkdir /mnt/c/Users/username/eclipse-workspace-work/YAMLAnalyser/%s && mv /mnt/c/work/diags/%s/*%s /mnt/c/Users/username/eclipse-workspace-work/YAMLAnalyser/%s/",
				ticketNumber, INPUT_FILE_POSTFIX, ticketNumber, ticketNumber, INPUT_FILE_POSTFIX, ticketNumber);

		// Download diags to project root folder
		if (WSLCommandRunner.syncSafely(fullCommand) == 0) {
			System.out.printf("Diags downloaded for %s successfully!%n", ticketNumber);
			return 0;
		} else {
			System.out.printf("Error downloading diags for %s%n", ticketNumber);
			return 1;
		}
	}

	public static int moveDiags(String ticketNumber) {

		String fullCommand = String.format(
				"mv /mnt/c/Users/username/eclipse-workspace-work/YAMLAnalyser/%s/*.txt /mnt/c/work/diags/%s/",
				ticketNumber, ticketNumber);

		if (WSLCommandRunner.syncSafely(fullCommand) == 0) {
			return 0;
		} else {
			return -1;
		}
	}

	public static int uploadEngineerDiags(String ticketNumber) {

		// Only .txt files left should be for engineer, so upload all
		String fullCommand = String.format("cd /mnt/c/work/diags/%s/ && SyncSafely.sh -n %s -f %s -d up", ticketNumber,
				ticketNumber, ".txt");

		// Download diags to project root folder
		if (WSLCommandRunner.syncSafely(fullCommand) == 0) {
			return 0;
		} else {
			return -1;
		}
	}

	public static int uploadCustomerDiags(String ticketNumber, String fileName) {
		String fullCommand = String.format(
				// Upload customer diags folder to ticket (public-facing)
				// Remove file after upload so only .txt files left are for engineer
				"cd /mnt/c/work/diags/%s && SyncSafely.sh -n %s -f %s%s -d up -public && rm -rf %s%s", ticketNumber,
				ticketNumber, fileName, OUTPUT_POSTFIX_CUSTOMER, fileName, OUTPUT_POSTFIX_CUSTOMER);

		// Download diags to project root folder
		if (WSLCommandRunner.syncSafely(fullCommand) == 0) {
			return 0;
		} else {
			return -1;
		}
	}

	public static List<Product> setProducts() {
		// List of products we care about
		List<String> products = List.of("product1", "product2");

		// Declare List to hold our products
		List<Product> productsList = new ArrayList<>();

		// Loop through products List
		for (String productType : products) {
			// Create new product object
			Product product = new Product(productType);

			// Update location per product
			switch (productType) {
			case ("product1"):
				product.addToSecretMap("user-accounts.password",
						"Please follow this MOP");
				product.addToSecretMap("user-accounts.password-id",
						"Please follow this MOP");
				product.addToSecretMap("user-accounts.root-password",
						"*SUPPORT ENGINEER ONLY* Please follow this MOP to assist customer.");
				break;
			case ("product2"):
				product.addToSecretMap("user-accounts.password",
						"Please follow this MOP");
				product.addToSecretMap("user-accounts.root-password",
						"*SUPPORT ENGINEER ONLY* Please follow this MOP to assist customer.");
				break;
			}
			// Add product object to productsList
			productsList.add(product);
		}

		return productsList;

	}

	public static Map<String, String> setSecretMapSiteWide() {
		// Declare variable(s)
		Map<String, String> siteWideSecrets;

		// Initialize hashmap for our siteWideScrets
		siteWideSecrets = new HashMap<String, String>();

		// Add site-wide secrets
		siteWideSecrets.put("x.site-wide-secret1",
				"Please follow this MOP");
		siteWideSecrets.put("y.site-wide-secret",
				"Please follow this MOP");

		return siteWideSecrets;
	}
	

	public static void analyseSiteWideSecrets(File file, BufferedWriter bwCustomer, BufferedWriter bwEngineer,
			Map<String, Object> siteParameters) throws IOException {
		// Declare variable(s)
		Map<String, String> siteWideSecrets;
		List<String> specialCases = new ArrayList<String>();

		// Set Secret Map for site wide thing
		siteWideSecrets = setSecretMapSiteWide();

		bwCustomer.write("Checking site-wide secrets\n");
		bwCustomer.write("------------------------------------------------------\n");

		for (String siteSecretPath : siteWideSecrets.keySet()) {

			// Split based on '.'
			String[] splitSite = siteSecretPath.split("\\.");

			// Determine how many layers deep we need to go
			int splitLengthSite = splitSite.length;

			// Initialise currentLayer Map to provided Map
			Map<String, Object> currentLayer = siteParameters;

			// Initialise value to null
			Object value = null;

			// Loop through all of the layers
			for (int i = 0; i < splitLengthSite; i++) {
				// Terminate if currentLayer is null
				if (currentLayer == null) {
					break;
				}

				// Set value = value at current layer
				value = currentLayer.get(splitSite[i]);

				if (i < splitLengthSite - 1) {
					// Aren't at the last layer yet so keep digging
					if (value instanceof Map) {
						// If current value is an instance of Map, cast it as a Map and update current layer
						currentLayer = (Map<String, Object>) value;
					} else if (value instanceof List) {
						System.err.println("Site: List Condition hit for "+ file.getName()
						+ ": " + value);
						specialCases.add(siteSecretPath);
					} else {
						// If it's not a Map or list, set the currentLayer to null
						currentLayer = null;
					}
				}
			}

			// Either currentLayer is null or we have
			if (value == null) {
			} else if (value instanceof String) {
				if (!siteWideSecrets.get(siteSecretPath).contains(ENGINEER_FLAG)) {
					bwCustomer
							.write(String.format("\t- %s: %s%n", siteSecretPath, siteWideSecrets.get(siteSecretPath)));
				} else {
					// Non-customer performable, so write to engineer.txt
					bwEngineer.write("Checking site-wide secrets\n");
					bwEngineer.write("------------------------------------------------------\n");
					bwEngineer.write("\n------------------------------------------------------\n");
					bwEngineer.write(String.format("\t- %s: %s", siteSecretPath, siteWideSecrets.get(siteSecretPath)));
				}
			} else {
				System.out.printf("Value at path %s is not a String or couldn't be resolved.%n", siteSecretPath);
			}

		}
		
		if (specialCases.size() != 0) {
			bwEngineer.write(
					"\n------------------------------------------------------\n");
			bwEngineer.write("*** Special Cases Per Site ***\n");
			for (String path : specialCases) {
				bwEngineer.write("- " + path + "\n");
			}
		}

		bwCustomer.write("------------------------------------------------------\n");
	}

	public static void analyseYaml(File file, BufferedReader br, BufferedWriter bwCustomer, BufferedWriter bwEngineer,
			String ticketID) throws IOException {
		// Declare variables
		String line;
		List<Product> productsList;
		String regex;
		Pattern pattern;
		boolean secretsFound;
		List<String> specialCases = new ArrayList<String>();

		// Setup the Regex for later on
		regex = "https?:\\/\\/([^:\\/\\s]+):([^@\\/\\s]+)@.*";
		pattern = Pattern.compile(regex);

		// Get our list of Products complete with secret mapping
		productsList = setProducts();

		// Create yaml object
		Yaml yaml = new Yaml();

		// Call load method with buffered reader as argument and return root map
		Map<String, Object> root = yaml.load(br);

		// Get Map associated with 'deployment' key
		Map<String, Object> deployment = (Map<String, Object>) root.get("deployment");

		// Get a list of all the sites
		List<Map<String, Object>> sites = (List<Map<String, Object>>) deployment.get("location");

		for (Map<String, Object> site : sites) {

			bwCustomer.write("\n------------------------------------------------------\n");
			bwCustomer.write(String.format("*** SITE NAME: %s ***", site.get("name")));
			bwCustomer.write("\n------------------------------------------------------\n\n");

			// Site-wide secrets Map
			Map<String, Object> siteParameters = (Map<String, Object>) site.get("parameters");

			// Analyse the site wide secrets
			analyseSiteWideSecrets(file, bwCustomer, bwEngineer, siteParameters);

			bwCustomer.newLine();
			bwCustomer.write("\n\nChecking per-product secrets");

			// vnfcs is a list of hashmaps
			List<Map<String, Object>> vnfcs = (List<Map<String, Object>>) site.get("product");

			// For each individual product in vnfcs
			for (Map<String, Object> productVNFCS : vnfcs) {

				// Get the name of the specific product
				String productName = (String) productVNFCS.get("name");

				// Get the product-options hashmap specific to each product
				Map<String, Object> productOptions = (Map<String, Object>) productVNFCS.get("product-options");

				Map.Entry<String, Object> entry = productOptions.entrySet().iterator().next();
				String key = entry.getKey();

				// Get value associated with product key
				Map<String, Object> productOptionsSpecific = (Map<String, Object>) productOptions.get(key);

				for (Product product : productsList) {

					// Initialise/reset our secrets found flag to false for each product
					secretsFound = false;

					if (key.contentEquals(product.getType())) {
						try {
							bwCustomer.write("\n------------------------------------------------------\n");
							bwCustomer.write(String.format("Name: %s%n%n", productName));

							// Per VNFC
							String softwareInstallLocation = (String) productVNFCS.get("a");
							String softwareInstallLocationID = (String) productVNFCS
									.get("b");
							String efixInstallLocation = (String) productVNFCS.get("x");
							String efixInstallLocationID = (String) productVNFCS.get("y");

							if (softwareInstallLocation != null) {

								Matcher matcherSI = pattern.matcher(softwareInstallLocation);

								if (matcherSI.matches()) {
									bwCustomer.write(
											"\t- a: Please update the credentials for the user specified in the URL on the HTTP server.\n");
									secretsFound = true;
								}
							}

							if (softwareInstallLocationID != null) {

								Matcher matcherSIiD = pattern.matcher(softwareInstallLocationID);

								if (matcherSIiD.matches()) {
									bwCustomer.write(
											"\t- b: Please update the credentials for the user specified in the URL on the HTTP server.\n");
									secretsFound = true;
								}
							}

							if (efixInstallLocation != null) {

								Matcher matcherEF = pattern.matcher(efixInstallLocation);

								if (matcherEF.matches()) {
									bwCustomer.write(
											"\t- x: Please update the credentials for the user specified in the URL on the HTTP server.\n");
									secretsFound = true;
								}
							}

							if (efixInstallLocationID != null) {

								Matcher matcherEFiD = pattern.matcher(efixInstallLocationID);

								if (matcherEFiD.matches()) {
									bwCustomer.write(
											"\t- y: Please update the credentials for the user specified in the URL on the HTTP server.\n");
									secretsFound = true;
								}
							}

							// Looking at relevant secrets for each product
							for (String secretLocationPath : product.getSecretMap().keySet()) {

								// Split based on '.'
								String[] split = secretLocationPath.split("\\.");

								// Determine how many layers deep we need to go
								int splitLength = split.length;

								if (splitLength == 1) {
									// Only one layer deep, so can immediately get value (if key exists)

									if (productOptionsSpecific.get(split[0]) == null) {
//										bw.write(String.format("Nothing found in %s!%n", secretLocationPath));
									} else {
										// Key exists
										String value = (String) productOptionsSpecific.get(split[0]);

										if (!product.getSecretMap().get(secretLocationPath).contains(ENGINEER_FLAG)) {
											// Customer-performable so write to customer.txt
											bwCustomer.write(String.format("\t- %s: %s%n", secretLocationPath,
													product.getSecretMap().get(secretLocationPath)));
											secretsFound = true;
										} else {
											// Non-customer performable so write to engineer.txt
											bwEngineer.write(
													"\n------------------------------------------------------\n");
											bwEngineer.write(String.format("Name: %s%n%n", productName));
											bwEngineer.write(String.format("\t- %s: %s%n", secretLocationPath,
													product.getSecretMap().get(secretLocationPath)));
										}
									}
								} else {
									// >1 layer deep

									Map<String, Object> currentLayer = productOptionsSpecific;
									Object value = null;

									// Check for Maps
									for (int i = 0; i < splitLength; i++) {
										if (currentLayer == null) {
											break;
										}

										value = currentLayer.get(split[i]);

										if (i < splitLength - 1) {
											// Keep digging only if not at the last layer
											if (value instanceof Map) {
												currentLayer = (Map<String, Object>) value;
											} else {
												currentLayer = null;
											}
										}
									}

									// Check for null value
									if (value == null) {
//										bw.write(String.format("Nothing found in %s!%n", secretLocationPath));
										// Check for String values
									} else if (value instanceof String) {
										if (!product.getSecretMap().get(secretLocationPath).contains(ENGINEER_FLAG)) {
											bwCustomer.write(String.format("\t- %s: %s%n", secretLocationPath,
													product.getSecretMap().get(secretLocationPath)));
											secretsFound = true;
										} else {
											bwEngineer.write(
													"\n------------------------------------------------------\n");
											bwEngineer.write(String.format("Name: %s%n%n", productName));
											bwEngineer.write(String.format("\t- %s: %s%n", secretLocationPath,
													product.getSecretMap().get(secretLocationPath)));
										}
										// Check for list values
										// *** Script cannot cope with nested lists ***
									} else if (value instanceof List) {
										System.err.println("Product: List Condition hit for " + ticketID + ": " + file.getName()
												+ ": " + secretLocationPath);
										// Add path to special cases array list
										specialCases.add(String.format("Product Type: %s, Product Name: %s, Path: %s", key, productName, secretLocationPath));

									} else {
										System.out.printf(
												"Value at path %s is not a String or List or couldn't be resolved.%n",
												secretLocationPath);
									}
								}
							}

							// Check for absence of Secrets found flag
							if (!secretsFound) {
								bwCustomer.write("\t- No secrets found!\n");
							}

						} catch (IOException e) {
							System.out.println("Error writing to file:");
							e.printStackTrace();
						}
					}
				}
			}
		}
		
		if (specialCases.size() != 0 ) {
			// Some special cases have been hit, so notify the engineer. 
			bwEngineer.write(
					"\n------------------------------------------------------\n");
			bwEngineer.write("*** Special Cases Per Product ***\n\n");
			for (String path : specialCases) {
				bwEngineer.write("- " + path + "\n");
			}
		}

		System.out.printf("Output File(s) created for: %s%n", file.getName());
	}

	public static void fdAPI(String ticketID, String body, boolean isPrivate)
			throws URISyntaxException, ParseException, IOException, InterruptedException {
		// Declare/initialise variable(s)
		String apiKey = System.getenv("FRESHDESK_API_KEY");
		String base64Key = Base64.getEncoder().encodeToString(apiKey.getBytes());
		String formattedBody = String.format(body, ticketID);

		String url = String.format("https://%s.freshdesk.com/api/v2/tickets/%s/notes", DOMAIN, ticketID);

		// Create URL object
		URI uri = new URI(url);

		// JSON payload for updating the conversation
		String json = """
				{
				    "body": "%s",
				    "private": %s
				}
				""".formatted(formattedBody, isPrivate);

		// Build PUT request with JSON body
		HttpRequest request = HttpRequest.newBuilder(uri).header("Content-Type", "application/json")
				.header("Authorization", "Basic " + base64Key + ":X").timeout(Duration.ofSeconds(3))
				.POST(HttpRequest.BodyPublishers.ofString(json)).build();

		HttpClient client = HttpClient.newHttpClient();
		HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
	}
}
