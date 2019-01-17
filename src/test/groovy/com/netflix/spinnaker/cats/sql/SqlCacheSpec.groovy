package com.netflix.spinnaker.cats.sql

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.WriteableCacheSpec
import com.netflix.spinnaker.cats.sql.cache.SqlCache
import com.netflix.spinnaker.cats.sql.cache.SqlCacheMetrics
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import spock.lang.AutoCleanup
import spock.lang.Shared

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

import static com.netflix.spinnaker.kork.sql.test.SqlTestUtil.*

class SqlCacheSpec extends WriteableCacheSpec {

  SqlCacheMetrics cacheMetrics = Mock()

  @Shared
  @AutoCleanup("close")
  TestDatabase currentDatabase

  def setup() {
    (getSubject() as SqlCache).clearCreatedTables()
    return initDatabase("jdbc:h2:mem:test")
  }


  def cleanup() {
    currentDatabase.context.dropSchemaIfExists("test")
  }

  def 'should not write an item if it is unchanged'() {
    setup:
    def data = createData('blerp', [a: 'b'])

    when:
    ((SqlCache) cache).merge('foo', data)

    then:
    1 * cacheMetrics.merge('test', 'foo', 1, 0, 2, 1, 0, 0)

    when:
    ((SqlCache) cache).merge('foo', data)

    then:
    // SqlCacheMetrics currently sets items to # of items stored. The redis impl
    // sets this to # of items passed to merge, regardless of how many are actually stored
    // after deduplication. TODO: Having both metrics would be nice.
    1 * cacheMetrics.merge('test', 'foo', 0, 0, 1, 0, 0, 0)
  }

  @Override
  Cache getSubject() {
    def mapper = new ObjectMapper()
    def clock = new Clock.FixedClock(Instant.EPOCH, ZoneId.of("UTC"))
    def sqlRetryProperties = new SqlRetryProperties()
    currentDatabase = initDatabase()
    return new SqlCache(
      "test",
      currentDatabase.context,
      mapper,
      clock,
      sqlRetryProperties,
      "test",
      cacheMetrics,
      10,
      10
    )
  }

}
