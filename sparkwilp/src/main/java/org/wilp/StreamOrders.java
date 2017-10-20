package org.wilp;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.apache.nifi.remote.client.SiteToSiteClient;
import org.apache.nifi.remote.client.SiteToSiteClientConfig;
import org.apache.nifi.spark.NiFiDataPacket;
import org.apache.nifi.spark.NiFiReceiver;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.VoidFunction;
import org.apache.spark.mllib.fpm.FPGrowth;
import org.apache.spark.mllib.fpm.FPGrowthModel;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.storage.StorageLevel;
import org.apache.spark.streaming.Duration;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaReceiverInputDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;


@SuppressWarnings("rawtypes")
public class StreamOrders {

	@SuppressWarnings({ "unchecked", "serial" })
	public static void main(String[] args) {

		try {
			SiteToSiteClientConfig config = new SiteToSiteClient.Builder().url("http://127.0.0.1:8081/nifi/")
					.portName("CustomerData").buildConfig();

			SparkConf sparkConf = new SparkConf().setMaster("local[10]")
					.setAppName("NiFi-Spark Streaming WILP example");
			JavaStreamingContext ssc = new JavaStreamingContext(sparkConf, new Duration(1000L));
			SparkSession sparkSession = SparkSession.builder().config(sparkConf).getOrCreate();

			// Create a JavaReceiverInputDStream using a NiFi receiver so that we can pull
			// data from
			// specified Port

			JavaReceiverInputDStream packetStream = ssc
					.receiverStream(new NiFiReceiver(config, StorageLevel.MEMORY_AND_DISK()));

			// Map the data from NiFi to text, ignoring the attributes
			JavaDStream text = packetStream.map(new Function() {
				@Override
				public List call(final Object dataPacket) throws Exception {

					BufferedReader bufReader = new BufferedReader(new StringReader(
							new String(((NiFiDataPacket) dataPacket).getContent(), StandardCharsets.UTF_8)));

					List transactions = new ArrayList();

					String line = null;
					while ((line = bufReader.readLine()) != null) {

						List<String> order = new ArrayList<String>();

						String[] coverages = line.split(",");

						for (int i = 10; i < coverages.length; i++) {

							order.add(coverages[i]);

						}
						transactions.add(order);
					}

					return transactions;
				}
			});

			text.foreachRDD(new VoidFunction<JavaRDD<List<List<String>>>>() {
				@Override
				public void call(JavaRDD<List<List<String>>> rdd) throws Exception {

					rdd.collect().forEach(new Consumer<List<List<String>>>() {

						@Override
						public void accept(List<List<String>> t) {
							// TODO Auto-generated method stub

							JavaRDD<List<String>> transformations1 = ssc.sparkContext().parallelize(t);

							FPGrowth fpg = new FPGrowth().setMinSupport(0.1).setNumPartitions(1);
							FPGrowthModel<String> model = fpg.run(transformations1);

						
							boolean dbUpdated = new UpdateToDB() {

								@Override
								public void doProcess(Connection c) throws SQLException {
									// TODO Auto-generated method stub

									Statement stmt = null;

									for (FPGrowth.FreqItemset<String> itemset : model.freqItemsets().toJavaRDD()
											.collect()) {
										System.out.println("[" + itemset.javaItems() + "], " + itemset.freq());

										stmt = c.createStatement();
										String sql = "INSERT INTO CA_MINEDDATA (code,fp) " + "VALUES ('" + "["
												+ itemset.javaItems() + "]" + "', " + itemset.freq() + " );";
										stmt.executeUpdate(sql);

									}

									stmt.close();

								}
							}.dataProcessor();
							
							

						}
					});

				}
			});

			ssc.start();

			ssc.awaitTermination();

		} catch (Exception e) {
			e.printStackTrace();

		}

	}
}
