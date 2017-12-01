package com.google.cloud.iberia;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.apache.commons.codec.binary.Hex;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.spanner.DatabaseAdminClient;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.DatabaseId;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.ReadOnlyTransaction;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.Spanner;
import com.google.cloud.spanner.SpannerOptions;
import com.google.cloud.spanner.Statement;
import com.google.cloud.spanner.TimestampBound;
import com.google.cloud.spanner.TransactionContext;
import com.google.cloud.spanner.TransactionRunner.TransactionCallable;

public class SpannerPlayground {

	public static void main(String[] args) throws Exception {

		// Authentication with Default Credentials
		try {
			GoogleCredentials.getApplicationDefault();	
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		SpannerOptions options = SpannerOptions.newBuilder().build();

		// Authentication with a service account
		//		String path = "File_Path";
		//      SpannerOptions.Builder optionsBuilder = SpannerOptions.newBuilder().setCredentials(GoogleCredentials.fromStream(new FileInputStream(path)));
		//		SpannerOptions options = optionsBuilder.build();
		//      optionsBuilder.setProjectId("projectId");

		Spanner spanner = options.getService();

		String instanceId = "spanner-demo-rafa";
		String databaseId = "demo";

		try {
			SpannerPlayground spannerPlayground = new SpannerPlayground();
			spannerPlayground.createDB(spanner, options.getProjectId(), instanceId, databaseId);
			spannerPlayground.insertData(spanner, options.getProjectId(), instanceId, databaseId);
			//spannerPlayground.readOnlyFunc(spanner, options.getProjectId(), instanceId, databaseId);
			//spannerPlayground.readWriteTXN(spanner, options.getProjectId(), instanceId, databaseId);
		} finally {
			spanner.close();
		}
		System.out.println("Closed client");
	}

	public void readOnlyFunc(Spanner spanner, String projectId, String instanceId, String databaseId) {
		DatabaseClient dbClient = spanner.getDatabaseClient(DatabaseId.of(projectId, instanceId, databaseId));

		ReadOnlyTransaction txn = dbClient.singleUseReadOnlyTransaction(TimestampBound.ofMaxStaleness(15, TimeUnit.SECONDS));		
		ResultSet resultSet = txn.executeQuery(
				Statement.newBuilder(
						"SELECT a.Name, a.Email FROM Account a, Address ad " +
						"WHERE a.AccountId = ad.AccountId AND ad.CountryCode = @country LIMIT 10")
				.bind("country").to("PT").build());
		while (resultSet.next()) {
			System.out.printf("%s %s\n", resultSet.getString(0), resultSet.getString(1));
		}
	}

	public void readWriteTXN(Spanner spanner, String projectId, String instanceId, String databaseId) {
		DatabaseClient dbClient = spanner.getDatabaseClient(DatabaseId.of(projectId, instanceId, databaseId));

		dbClient.readWriteTransaction().run(
				new TransactionCallable<Void>() {
					@Nullable
					@Override
					public Void run(TransactionContext txn) throws Exception {
						ResultSet resultSet = txn.executeQuery(
								Statement.newBuilder(
										"SELECT * FROM Account WHERE email=@email")
								.bind("email").to("myname@mydomain.com")
								.build());
						List<Mutation> mutations = new ArrayList<>();
						while (resultSet.next()) {
							mutations.add(Mutation.newUpdateBuilder("Account")
									.set("AccountId").to(resultSet.getString("AccountId"))
									.set("Name").to("SpannerGuru")
									.build());
						}
						txn.buffer(mutations);
						return null;
					}
				});
	}

	public void insertData(Spanner spanner, String projectId, String instanceId, String databaseId) {
		DatabaseClient dbClient = spanner.getDatabaseClient(DatabaseId.of(projectId, instanceId, databaseId));

		String aID = getBase64EncodedUUID();
		List<Mutation> mutations = new ArrayList<>();
		mutations.add(Mutation.newInsertBuilder("Account")
				.set("AccountId").to(aID)
				.set("Name").to("Robert")
				.set("Email").to("myname@mydomain.com")
				.build());
		mutations.add(Mutation.newInsertBuilder("Address")
				.set("AccountId").to(aID)
				.set("AddressId").to(getBase64EncodedUUID())
				.set("Street").to("My address")
				.set("City").to("Lisboa")
				.set("ZIP").to("28020")
				.set("CountryCode").to("PT")
				.build());
		// This writes all the mutations to Cloud Spanner atomically. Get a timestamp, but I skip it
		dbClient.write(mutations);
	}


	public void createDB(Spanner spanner, String projectId, String instanceId, String databaseId) {
		DatabaseAdminClient adminClient = spanner.getDatabaseAdminClient();

		adminClient.createDatabase(instanceId, databaseId, 
				Arrays.asList(
						"CREATE TABLE Account (\n"
								+ "  AccountId  STRING(32) NOT NULL,\n"
								+ "  Name       STRING(256) NOT NULL,\n"
								+ "  Email      STRING(256) NOT NULL,\n"
								+ ") PRIMARY KEY (AccountId)",
								"CREATE TABLE Address (\n"
										+ "  AccountId   STRING(32) NOT NULL,\n"
										+ "  AddressId   STRING(32) NOT NULL,\n"
										+ "  Street      STRING(1024) NOT NULL,\n"
										+ "  City        STRING(256) NOT NULL,\n"
										+ "  ZIP         STRING(20) NOT NULL,\n"
										+ "  CountryCode STRING(2) NOT NULL,\n"
										+ ") PRIMARY KEY (AccountId, AddressId),\n"
										+ "  INTERLEAVE IN PARENT Account ON DELETE CASCADE",
						"CREATE INDEX ByCountry ON Address(CountryCode) STORING (City, ZIP)")).waitFor();
		System.out.println("Created database [" + databaseId + "]");
	}

	public static String getBase64EncodedUUID() {
		UUID uuid = UUID.randomUUID();
		ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
		bb.putLong(uuid.getMostSignificantBits());
		bb.putLong(uuid.getLeastSignificantBits());
		return new String(Hex.encodeHex(bb.array()));
	}
}
