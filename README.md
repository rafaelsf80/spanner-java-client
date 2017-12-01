# Google Cloud Java Client for Spanner

Java client for [Cloud Spanner][cloud-spanner].

[![Build Status](https://travis-ci.org/GoogleCloudPlatform/google-cloud-java.svg?branch=master)](https://travis-ci.org/GoogleCloudPlatform/google-cloud-java)
[![Coverage Status](https://coveralls.io/repos/GoogleCloudPlatform/google-cloud-java/badge.svg?branch=master)](https://coveralls.io/r/GoogleCloudPlatform/google-cloud-java?branch=master)
[![Maven](https://img.shields.io/maven-central/v/com.google.cloud/google-cloud-spanner.svg)](https://img.shields.io/maven-central/v/com.google.cloud/google-cloud-spanner.svg)
[![Codacy Badge](https://api.codacy.com/project/badge/grade/9da006ad7c3a4fe1abd142e77c003917)](https://www.codacy.com/app/mziccard/google-cloud-java)
[![Dependency Status](https://www.versioneye.com/user/projects/58fe4c8d6ac171426c414772/badge.svg?style=flat)](https://www.versioneye.com/user/projects/58fe4c8d6ac171426c414772)

> Note: This client is a work-in-progress

## About Cloud Spanner

[Cloud Spanner][cloud-spanner] is a fully managed, mission-critical relational database service
built from the ground up and battle tested for transactional consistency, high
availability, and global scale. With traditional relational semantics (schemas,
ACID transactions, SQL) and automatic, synchronous replication for high
availability, Cloud Spanner is the only database service of its kind on the
market.

Be sure to activate the Cloud Spanner API on the Developer's Console to
use Cloud Spanner from your project.

See the [Spanner client lib docs][spanner-client-lib-docs] and the [Product Documentation][spanner-product-docs] to learn how to
interact with Cloud Spanner using this Client Library.


## Authentication

Below is a code snippet to make authentication using Default Credentials:

```java
	GoogleCredentials.getApplicationDefault();	
	SpannerOptions options = SpannerOptions.newBuilder().build();

```

And below using a Service Account:
```java
	String path = "File_Path";
	SpannerOptions.Builder optionsBuilder = SpannerOptions.newBuilder().setCredentials(GoogleCredentials.fromStream(new FileInputStream(path)));
	SpannerOptions options = optionsBuilder.build();
	optionsBuilder.setProjectId("projectId");
```

Refer also to the
[Authentication](https://github.com/GoogleCloudPlatform/google-cloud-java#authentication)
section for the Google Cloud Java client library.

## Transactions

Spanner supports two types of read-only transactions: strong and stale. A stale read is read at a timestamp in the past. If your application is latency sensitive but tolerant of stale data, then stale reads can provide performance benefits. Read-only transactions don't write, they don't hold locks and they don't block other transactions. Read-only transactions observe a consistent prefix of the transaction commit history, so your application always gets consistent data. Below a read-only transaction with 15 sec stale:

```java
	ReadOnlyTransaction txn = dbClient.singleUseReadOnlyTransaction(TimestampBound.ofMaxStaleness(15, TimeUnit.SECONDS));		
	ResultSet resultSet = txn.executeQuery(
		Statement.newBuilder(
			"SELECT a.Name, a.Email FROM Account a, Address ad " +
			"WHERE a.AccountId = ad.AccountId AND ad.CountryCode = @country LIMIT 10")
		.bind("country").to("PT").build());

```

In addition to read-only transactions, Cloud Spanner offers locking read-write transactions. This type of transaction is the only transaction type that supports writing data into Cloud Spanner. These transactions rely on pessimistic locking and, if necessary, two-phase commit. Locking read-write transactions may abort, requiring the application to retry:

```java
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
```




## License

Apache 2.0 - See [LICENSE] for more information.

[cloud-spanner]: https://cloud.google.com/spanner/
[spanner-product-docs]: https://cloud.google.com/spanner/docs/
[spanner-client-lib-docs]: https://googlecloudplatform.github.io/google-cloud-java/latest/apidocs/index.html?com/google/cloud/spanner/package-summary.html