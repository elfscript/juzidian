/*
 * Copyright Nathan Jones 2012
 *
 * This file is part of Juzidian.
 *
 * Juzidian is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Juzidian is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Juzidian.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.juzidian.core;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.juzidian.core.DictionaryDataStoreSchemaCreator;

import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.support.DatabaseConnection;

public class DictionaryDataStoreSchemaCreatorTest {

	private JdbcConnectionSource connectionSource;

	private DictionaryDataStoreSchemaCreator dbDictionaryDataStoreSchemaCreator;

	@Before
	public void setUp() throws Exception {
		this.connectionSource = new JdbcConnectionSource("jdbc:sqlite::memory:");
		this.dbDictionaryDataStoreSchemaCreator = new DictionaryDataStoreSchemaCreator();
	}

	@Test
	public void createSchemaShouldCreateEntryTableInEmptyDatabase() throws Exception {
		Assert.assertFalse(this.connectionSource.getReadOnlyConnection().isTableExists("dictionary_entry"));
		this.dbDictionaryDataStoreSchemaCreator.createSchema(this.connectionSource);
		Assert.assertTrue(this.connectionSource.getReadOnlyConnection().isTableExists("dictionary_entry"));
	}

	@Test
	public void createSchemaShouldCreateEntryTableInPopulatedDatabase() throws Exception {
		this.connectionSource.getReadWriteConnection().executeStatement("create table dictionary_entry(id string)",
				DatabaseConnection.DEFAULT_RESULT_FLAGS);
		Assert.assertTrue(this.connectionSource.getReadOnlyConnection().isTableExists("dictionary_entry"));
		this.dbDictionaryDataStoreSchemaCreator.createSchema(this.connectionSource);
		Assert.assertTrue(this.connectionSource.getReadOnlyConnection().isTableExists("dictionary_entry"));
	}

	@Test
	public void createSchemaShouldCreateMetadataTableInEmptyDatabase() throws Exception {
		Assert.assertFalse(this.connectionSource.getReadOnlyConnection().isTableExists("dictionary_metadata"));
		this.dbDictionaryDataStoreSchemaCreator.createSchema(this.connectionSource);
		Assert.assertTrue(this.connectionSource.getReadOnlyConnection().isTableExists("dictionary_metadata"));
	}

	@Test
	public void createSchemaShouldCreateMetadataTableInPopulatedDatabase() throws Exception {
		this.connectionSource.getReadWriteConnection().executeStatement("create table dictionary_metadata(id string)",
				DatabaseConnection.DEFAULT_RESULT_FLAGS);
		Assert.assertTrue(this.connectionSource.getReadOnlyConnection().isTableExists("dictionary_metadata"));
		this.dbDictionaryDataStoreSchemaCreator.createSchema(this.connectionSource);
		Assert.assertTrue(this.connectionSource.getReadOnlyConnection().isTableExists("dictionary_metadata"));
	}

}
