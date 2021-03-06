package org.elasticsearch.test.integration.search.simple

import org.elasticsearch.index.query.QueryBuilders._
import scalastic.elasticsearch._

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class SimpleSearchTests extends IndexerBasedTest {

  test("simpleIpTests") {
    indexer.putMapping(indexName, "type1", """{"type1": {"properties": {"from": {"type": "ip"}, "to": {"type": "ip"}}}}""")
    indexer.index(indexName, "type1", "1", """{"from": "192.168.0.5", "to": "192.168.0.10"}""", refresh = Some(true))
    indexer.refresh()
    indexer.search(query = boolQuery().must(rangeQuery("from") lt "192.168.0.7").must(rangeQuery("to") gt "192.168.0.7")).getHits.totalHits should be === (1)
  }

  test("simpleIdTests") {
    indexer.deleteIndex(Seq(indexName))
    indexer.index(indexName, "type", "XXX1", """{"field": "value"}""", refresh = Some(true))
    indexer.refresh()
    val response = indexer.search(query = termQuery("_id", "XXX1"))
    response.getHits.totalHits should be === (1)
    search(termQuery("_id", "XXX1")).getHits.totalHits should equal(1)
    search(queryString("_id:XXX1")).getHits.totalHits should be === 1
    search(prefixQuery("_id", "XXX")).getHits.totalHits should be === 1
    search(queryString("_id:XXX*").lowercaseExpandedTerms(false)).getHits.totalHits should be === 1
  }

  test("simpleDateRangeWithUpperInclusiveEnabledTests") {
    indexer.index(indexName, "type1", "1", """{"field": "2010-01-05T02:00"}""")
    indexer.index(indexName, "type1", "2", """{"field": "2010-01-06T02:00"}""")
    indexer.refresh()
    var response = indexer.search(Seq(indexName), query = rangeQuery("field").gte("2010-01-05").lte("2010-01-06"))
    response.getHits.totalHits should be === (2)
    response = indexer.search(Seq(indexName), query = rangeQuery("field").gte("2010-01-05").lt("2010-01-06"))
    response.getHits.totalHits should be === (1)
  }

  test("simpleDateRangeWithUpperInclusiveDisabledTests") {
    val specialIndex = indexName + "_with_upper_inclusive_disabled"
    indexer.createIndex(specialIndex, settings = Map("mapping.date.parse_upper_inclusive" -> "false"))
    indexer.index(specialIndex, "type1", "1", """{"field":"2010-01-05T02:00"}""")
    indexer.index(specialIndex, "type1", "2", """{"field":"2010-01-06T02:00"}""")
    indexer.refresh()
    indexer.search(Seq(specialIndex), query = rangeQuery("field").gte("2010-01-05").lte("2010-01-06")).getHits.totalHits should be === 1
    indexer.search(Seq(specialIndex), query = rangeQuery("field").gte("2010-01-05") lt "2010-01-06").getHits.totalHits should be === 1
  }

  test("simpleDateMathTests") {
    indexer.index(indexName, "type1", "1", """{"field": "2010-01-05T02:00"}""")
    indexer.index(indexName, "type1", "2", """{"field": "2010-01-06T02:00"}""")
    indexer.refresh()
    search(rangeQuery("field").gte("2010-01-05").lte("2010-01-06")).getHits.totalHits should be === 2
    search(rangeQuery("field").gte("2010-01-05") lt "2010-01-06").getHits.totalHits should be === 1
  }
  
  test("dateMath") {
    indexer.index(indexName, "type1", "1", """{"field":"2010-01-05T02:00"}""")
    indexer.index(indexName, "type1", "2", """{"field":"2010-01-06T02:00"}""")
    indexer.refresh()
    indexer.search(query = rangeQuery("field").gte("2010-01-03||+2d").lte("2010-01-04||+2d")).getHits.totalHits should be === 2
    indexer.search(query = queryString("field:[2010-01-03||+2d TO 2010-01-04||+2d]")).getHits.totalHits should be === 2
  }

  test("text search") {
    indexer.index(indexName, "type1", "1", """{"field": "trying to find an exact match"}""")
    indexer.index(indexName, "type1", "2", """{"field": "trying to find, but failing to find, an exact match"}""")
    indexer.index(indexName, "type1", "3", """{"field": "don't try this at home!"}""")
    indexer.refresh()

    search("field:bla").getHits.totalHits should be === 0
    search("field:try").getHits.totalHits should be === 1
    search("trying").getHits.totalHits should be === 2
    search("trying to find an exact match").getHits.totalHits should be === 2
    search("\"trying to find an exact match\"").getHits.totalHits should be === 1

    search(matchPhraseQuery("field", "trying to find an exact match")).getHits.totalHits should be === 1
    search(matchPhraseQuery("field", "\"trying to find an exact match\"")).getHits.totalHits should be === 1
  }
  
  test("multiSearch") {
    indexer.index(indexName, "type", "1", """{"field":"xxx"}""")
    indexer.index(indexName, "type", "2", """{"field":"yyy"}""")
    indexer.refresh()
    val queries = List(termQuery("field", "xxx"), termQuery("field", "yyy"), matchAllQuery)
    val responses = indexer.multisearchByQuery(queries).getResponses
    responses.length should be === 3
    responses(0).getResponse.getHits.totalHits should be === 1
    responses(0).getResponse.getHits.getAt(0).id should be === "1"
    responses(1).getResponse.getHits.totalHits should be === 1
    responses(1).getResponse.getHits.getAt(0).id should be === "2"
    responses(2).getResponse.getHits.totalHits should be === 2
  }
}
