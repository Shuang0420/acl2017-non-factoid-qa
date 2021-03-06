package data

import java.net.InetAddress

import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.index.IndexNotFoundException
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.transport.client.PreBuiltTransportClient
import play.api.libs.json._
import readers.TextItem

class ElasticConnector(host: String, port: Int, indexName: String) {

  val client = new PreBuiltTransportClient(Settings.EMPTY)
    .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(host), port))


  def createIndex(deleteOldIndex: Boolean): Unit = {
    if (deleteOldIndex) {
      try {
        this.client.admin.indices.prepareDelete(indexName).get
      } catch {
        case e: IndexNotFoundException => // ok
      }
    }

    val r = this.client.admin.indices.prepareCreate(indexName).addMapping("answer", Mapping.answersMapping)
    r.get
    ()
  }

  def queryAnswers(query: String, count: Integer): Seq[TextItem] = {
    val response = client.prepareSearch(indexName)
      .setTypes("answer")
      .setQuery(QueryBuilders.matchQuery("text", query))
      .setSize(count)
      .execute
      .actionGet

    response.getHits.hits().map { hit =>
      Json.fromJson[TextItem](Json.parse(hit.getSourceAsString)).asOpt
    }.filter(_.isDefined).map(_.get)
  }

  def saveAnswers(answers: Seq[TextItem]): Either[String, String] = {
    val bulkRequest = client.prepareBulk
    answers.foreach { answer =>
      val answerJson = Json.toJson(answer).toString()
      bulkRequest.add(client.prepareIndex(this.indexName, "answer", answer.id).setSource(answerJson))
    }
    val response = bulkRequest.get
    if (response.hasFailures) {
      Right(response.buildFailureMessage())
    } else {
      Left("Index creation successful")
    }
  }
}

private object Mapping {
  val answersMapping =
    """{
      |  "answer": {
      |    "properties": {
      |      "id": {
      |        "type": "text"
      |      },
      |      "text": {
      |        "type": "text",
      |        "similarity": "BM25"
      |      }
      |    }
      |  }
      |}""".stripMargin
}
