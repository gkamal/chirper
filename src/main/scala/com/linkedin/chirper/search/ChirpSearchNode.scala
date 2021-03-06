package com.linkedin.chirper.search

import com.linkedin.led.twitter.config._

import org.json.JSONObject;

import proj.zoie.api.DefaultZoieVersion;
import proj.zoie.api.DefaultZoieVersion.DefaultZoieVersionFactory;
import proj.zoie.hourglass.impl.HourGlassScheduler;
import proj.zoie.hourglass.impl.HourGlassScheduler.FREQUENCY;
import proj.zoie.impl.indexing.ZoieConfig;

import java.util._
import java.text.SimpleDateFormat

import com.linkedin.norbert.javacompat.cluster.ClusterClient;
import com.linkedin.norbert.javacompat.cluster.ZooKeeperClusterClient;
import com.linkedin.norbert.javacompat.network.NettyNetworkServer;
import com.linkedin.norbert.javacompat.network.NetworkServer;
import com.sensei.search.nodes.impl.SenseiBuilderHelper
import com.sensei.search.nodes.SenseiHourglassFactory
import com.sensei.search.nodes.SenseiIndexLoaderFactory
import com.sensei.search.nodes.SenseiIndexReaderDecorator
import com.sensei.search.nodes.SenseiServer
import com.sensei.search.nodes.impl._

import java.io.File

// Build a search node
object ChirpSearchNode{
	def addShutdownHook(body: => Unit) = 
	  Runtime.getRuntime.addShutdownHook(new Thread {
	    override def run { body }
	})
	
	def main(args: Array[String]) = {
	
	  val nodeid = Config.readInt("search.node.id")
	  val port = Config.readInt("search.node.port")
	  val partList = Config.readString("search.node.partitions")
	
	  // where to put the index
	  val idxDir = new File(Config.readString("search.node.index.dir"))
	
	  // rolls daily at midnight, keep 7 days
	  val hfFactory = new SenseiHourglassFactory[JSONObject, DefaultZoieVersion](idxDir,ChirpSearchConfig.interpreter,
                          new SenseiIndexReaderDecorator(ChirpSearchConfig.handlerList,null), 
	                      ChirpSearchConfig.zoieConfig, "00 00 00", 7, FREQUENCY.DAILY)
	
	  val clusterName = Config.readString("zookeeper.cluster")
      val zkurl = Config.readString("zookeeper.url")
	  val timeout = 30000

      // zookeeper cluster client
      val clusterClient = new ZooKeeperClusterClient(clusterName,zkurl,timeout);

      // build a default netty-based network server
      val networkServer = SenseiBuilderHelper.buildDefaultNetworkServer(clusterClient);

      // class to hook up to kafka for events
	  val indexLoaderFactory = new ChirpIndexLoaderFactory();
		
      // builds the server
	  val server = new SenseiServer(nodeid, port, partList.split(",").map{i=>i.toInt},
			                      idxDir,networkServer,
			                      clusterClient,hfFactory,indexLoaderFactory,ChirpSearchConfig.queryBuilderFactory)
			
	  addShutdownHook{ server.shutdown }
	
	  // starts the server
	  server.start(true)
		
    }	
}

