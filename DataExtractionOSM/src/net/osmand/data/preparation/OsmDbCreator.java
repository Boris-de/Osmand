package net.osmand.data.preparation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import net.osmand.osm.Entity;
import net.osmand.osm.Node;
import net.osmand.osm.Relation;
import net.osmand.osm.Way;
import net.osmand.osm.Entity.EntityId;
import net.osmand.osm.Entity.EntityType;
import net.osmand.osm.io.IOsmStorageFilter;
import net.osmand.osm.io.OsmBaseStorage;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class OsmDbCreator implements IOsmStorageFilter {

	private static final Log log = LogFactory.getLog(OsmDbCreator.class);

	public static final int BATCH_SIZE_OSM = 10000;


	DBDialect dialect;
	int currentCountNode = 0;
	private PreparedStatement prepNode;
	int allNodes = 0;

	int currentRelationsCount = 0;
	private PreparedStatement prepRelations;
	int allRelations = 0;

	int currentWaysCount = 0;
	private PreparedStatement prepWays;
	int allWays = 0;

	int currentTagsCount = 0;
	private PreparedStatement prepTags;
	private Connection dbConn;
	private final IndexCreator indexCreator;


	public OsmDbCreator(IndexCreator indexCreator) {
		this.indexCreator = indexCreator;
	}

	public void initDatabase(DBDialect dialect, Connection dbConn) throws SQLException {
		this.dbConn = dbConn;
		this.dialect = dialect;
		// prepare tables
		Statement stat = dbConn.createStatement();
		dialect.deleteTableIfExists("node", stat);
		stat.executeUpdate("create table node (id bigint primary key, latitude double, longitude double)"); //$NON-NLS-1$
		stat.executeUpdate("create index IdIndex ON node (id)"); //$NON-NLS-1$
		dialect.deleteTableIfExists("ways", stat);
		stat.executeUpdate("create table ways (id bigint, node bigint, ord smallint, primary key (id, ord))"); //$NON-NLS-1$
		stat.executeUpdate("create index IdWIndex ON ways (id)"); //$NON-NLS-1$
		dialect.deleteTableIfExists("relations", stat);
		stat.executeUpdate("create table relations (id bigint, member bigint, type smallint, role varchar(255), ord smallint, primary key (id, ord))"); //$NON-NLS-1$
		stat.executeUpdate("create index IdRIndex ON relations (id)"); //$NON-NLS-1$
		dialect.deleteTableIfExists("tags", stat);
		stat.executeUpdate("create table tags (id bigint, type smallint, skeys varchar(255), value varchar(255), primary key (id, type, skeys))"); //$NON-NLS-1$
		stat.executeUpdate("create index IdTIndex ON tags (id, type)"); //$NON-NLS-1$
		stat.close();

		prepNode = dbConn.prepareStatement("insert into node values (?, ?, ?)"); //$NON-NLS-1$
		prepWays = dbConn.prepareStatement("insert into ways values (?, ?, ?)"); //$NON-NLS-1$
		prepRelations = dbConn.prepareStatement("insert into relations values (?, ?, ?, ?, ?)"); //$NON-NLS-1$
		prepTags = dbConn.prepareStatement("insert into tags values (?, ?, ?, ?)"); //$NON-NLS-1$
		dbConn.setAutoCommit(false);
	}

	public void finishLoading() throws SQLException {
		if (currentCountNode > 0) {
			prepNode.executeBatch();
		}
		prepNode.close();
		if (currentWaysCount > 0) {
			prepWays.executeBatch();
		}
		prepWays.close();
		if (currentRelationsCount > 0) {
			prepRelations.executeBatch();
		}
		prepRelations.close();
		if (currentTagsCount > 0) {
			prepTags.executeBatch();
		}
		prepTags.close();
	}

	@Override
	public boolean acceptEntityToLoad(OsmBaseStorage storage, EntityId entityId, Entity e) {
		// Register all city labelbs
		indexCreator.registerCityIfNeeded(e);
		// put all nodes into temporary db to get only required nodes after loading all data
		try {
			if (e instanceof Node) {
				currentCountNode++;
				if (!e.getTags().isEmpty()) {
					allNodes++;
				}
				prepNode.setLong(1, e.getId());
				prepNode.setDouble(2, ((Node) e).getLatitude());
				prepNode.setDouble(3, ((Node) e).getLongitude());
				prepNode.addBatch();
				if (currentCountNode >= BATCH_SIZE_OSM) {
					prepNode.executeBatch();
					dbConn.commit(); // clear memory
					currentCountNode = 0;
				}
			} else if (e instanceof Way) {
				allWays++;
				short ord = 0;
				for (Long i : ((Way) e).getNodeIds()) {
					currentWaysCount++;
					prepWays.setLong(1, e.getId());
					prepWays.setLong(2, i);
					prepWays.setLong(3, ord++);
					prepWays.addBatch();
				}
				if (currentWaysCount >= BATCH_SIZE_OSM) {
					prepWays.executeBatch();
					dbConn.commit(); // clear memory
					currentWaysCount = 0;
				}
			} else {
				allRelations++;
				short ord = 0;
				for (Entry<EntityId, String> i : ((Relation) e).getMembersMap().entrySet()) {
					currentRelationsCount++;
					prepRelations.setLong(1, e.getId());
					prepRelations.setLong(2, i.getKey().getId());
					prepRelations.setLong(3, i.getKey().getType().ordinal());
					prepRelations.setString(4, i.getValue());
					prepRelations.setLong(5, ord++);
					prepRelations.addBatch();
				}
				if (currentRelationsCount >= BATCH_SIZE_OSM) {
					prepRelations.executeBatch();
					dbConn.commit(); // clear memory
					currentRelationsCount = 0;
				}
			}
			for (Entry<String, String> i : e.getTags().entrySet()) {
				currentTagsCount++;
				prepTags.setLong(1, e.getId());
				prepTags.setLong(2, EntityType.valueOf(e).ordinal());
				prepTags.setString(3, i.getKey());
				prepTags.setString(4, i.getValue());
				prepTags.addBatch();
			}
			if (currentTagsCount >= BATCH_SIZE_OSM) {
				prepTags.executeBatch();
				dbConn.commit(); // clear memory
				currentTagsCount = 0;
			}
		} catch (SQLException ex) {
			log.error("Could not save in db", ex); //$NON-NLS-1$
		}
		// do not add to storage
		return false;
	}

	public int getAllNodes() {
		return allNodes;
	}

	public int getAllRelations() {
		return allRelations;
	}

	public int getAllWays() {
		return allWays;
	}
	

}
