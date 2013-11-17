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
package org.juzidian.core.datastore;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;

import javax.inject.Inject;

import org.juzidian.core.DictionaryDataStore;
import org.juzidian.core.DictionaryDataStoreException;
import org.juzidian.core.DictionaryDataStoreQueryCancelledException;
import org.juzidian.core.DictionaryEntry;
import org.juzidian.core.SearchCanceller;
import org.juzidian.pinyin.PinyinSyllable;
import org.juzidian.pinyin.Tone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.field.SqlType;
import com.j256.ormlite.misc.TransactionManager;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.SelectArg;
import com.j256.ormlite.support.CancellationSignaller;
import com.j256.ormlite.support.ConnectionSource;

public class DbDictionaryDataStore implements DictionaryDataStore {

	private static final Logger LOGGER = LoggerFactory.getLogger(DbDictionaryDataStore.class);

	private static final Long METADATA_ROW_ID = 1L;

	/**
	 * The sequential integer version number of the data format that is created
	 * by and expected by this data store.
	 * <p>
	 * The data format includes the database schema as well as the format of
	 * values within the database.
	 */
	public static final int DATA_FORMAT_VERSION = 0;

	private final Dao<DbDictionaryEntry, Long> dictionaryEntryDao;

	private final Dao<DbDictionaryMetadata, Long> dictionaryMetadataDao;

	@Inject
	public DbDictionaryDataStore(final Dao<DbDictionaryEntry, Long> dictionaryEntryDao,
			final Dao<DbDictionaryMetadata, Long> dictionaryMetadataDao) {
		this.dictionaryEntryDao = dictionaryEntryDao;
		this.dictionaryMetadataDao = dictionaryMetadataDao;
	}

	/**
	 * Create or re-create the database schema.
	 * <p>
	 * This operation will destroy all existing data.
	 */
	public void createSchema() {
		new DbDictionaryDataStoreSchemaCreator().createSchema(this.getConnectionSource());
	}

	private ConnectionSource getConnectionSource() {
		return this.dictionaryEntryDao.getConnectionSource();
	}

	/**
	 * Insert expected dictionary metadata into the database.
	 */
	public void populateMetadata() {
		LOGGER.debug("Populating DB metadata.");
		final DbDictionaryMetadata metadata = new DbDictionaryMetadata();
		metadata.setId(METADATA_ROW_ID);
		metadata.setVersion(DATA_FORMAT_VERSION);
		metadata.setBuildDate(new Date());
		this.saveMetadata(metadata);
	}

	/**
	 * Get the version number of the data format that is currently used in this
	 * data store's data.
	 * <p>
	 * The data format includes the database schema as well as the format of
	 * values within the database.
	 * 
	 * @return a sequential integer version number.
	 */
	public int getCurrentDataFormatVersion() {
		try {
			return this.dictionaryMetadataDao.queryForId(METADATA_ROW_ID).getVersion();
		} catch (final SQLException e) {
			throw new DictionaryDataStoreException("Failed to load datastore metadata", e);
		}
	}

	private void saveMetadata(final DbDictionaryMetadata metadata) {
		LOGGER.debug("Saving DB metadata: {}.", metadata);
		try {
			this.dictionaryMetadataDao.createOrUpdate(metadata);
		} catch (final SQLException e) {
			throw new DictionaryDataStoreException("Failed to create metadata", e);
		}
	}

	@Override
	public void add(final Collection<DictionaryEntry> entries) {
		LOGGER.debug(String.format("Adding %d entries to dictionary DB.", entries.size()));
		try {
			TransactionManager.callInTransaction(this.dictionaryEntryDao.getConnectionSource(), new BulkEntryAdd(entries));
		} catch (final SQLException e) {
			throw new DictionaryDataStoreException("Failed to add dictionary entries", e);
		}
	}

	private class BulkEntryAdd implements Callable<Void> {

		private final Collection<DictionaryEntry> entries;

		public BulkEntryAdd(final Collection<DictionaryEntry> entries) {
			this.entries = entries;
		}

		@Override
		public Void call() throws Exception {
			for (final DictionaryEntry dictionaryEntry : this.entries) {
				DbDictionaryDataStore.this.add(dictionaryEntry);
			}
			return null;
		}

	}

	public void add(final DictionaryEntry entry) {
		LOGGER.debug("Adding entry to dictionary DB: " + entry);
		final DbDictionaryEntry dbEntry = this.createDbEntry(entry);
		try {
			this.dictionaryEntryDao.create(dbEntry);
		} catch (final SQLException e) {
			throw new DictionaryDataStoreException("Failed to add dictionary entry: " + entry, e);
		}
	}

	private DbDictionaryEntry createDbEntry(final DictionaryEntry entry) {
		final DbDictionaryEntry dbEntry = new DbDictionaryEntry();
		dbEntry.setTraditional(entry.getTraditional());
		dbEntry.setSimplified(entry.getSimplified());
		dbEntry.setPinyin(this.formatPinyin(entry.getPinyin()));
		dbEntry.setEnglish(this.formatDefinitions(entry.getDefinitions()));
		return dbEntry;
	}

	private String formatPinyin(final List<PinyinSyllable> list) {
		final StringBuilder sb = new StringBuilder();
		for (final PinyinSyllable pinyinSyllable : list) {
			/*
			 * Add a leading space so that "pinyin contains" searches do not get
			 * false matches on similar pinyin syllables. For example, '*hao*'
			 * should not match 'zhao'.
			 */
			sb.append(" ").append(pinyinSyllable.getLetters()).append(pinyinSyllable.getTone().getNumber());
		}
		/*
		 * Add trailing space so that exact pinyin syllables can be
		 * distinguished in "like" query (and ordered accordingly). For example,
		 * "han*" should match all "han" syllables before matching any "hang"
		 * syllable.
		 */
		sb.append(" ");
		return sb.toString();
	}

	private String formatPinyinQuery(final List<PinyinSyllable> pinyinSyllables) {
		final StringBuilder sb = new StringBuilder();
		for (final PinyinSyllable pinyinSyllable : pinyinSyllables) {
			final Tone tone = pinyinSyllable.getTone();
			/* Use underscore to match "any" tone in an SQL "like" query. */
			final String toneSearchValue = Tone.ANY.equals(tone) ? "_" : tone.getNumber().toString();
			sb.append(" ").append(pinyinSyllable.getLetters()).append(toneSearchValue);
		}
		return sb.toString();
	}

	private List<PinyinSyllable> unformatPinyin(final String pinyin) {
		final String[] rawPinyin = pinyin.trim().split(" ");
		final List<PinyinSyllable> syllables = new LinkedList<PinyinSyllable>();
		for (final String letters : rawPinyin) {
			final PinyinSyllable syllable = this.parseSyllable(letters);
			syllables.add(syllable);
		}
		return syllables;
	}

	private PinyinSyllable parseSyllable(final String formattedPinyinSyllable) {
		final String pinyinLetters = formattedPinyinSyllable.substring(0, formattedPinyinSyllable.length() - 1);
		final int pinyinToneNumber = Integer.parseInt(formattedPinyinSyllable.substring(formattedPinyinSyllable.length() - 1));
		return new PinyinSyllable(pinyinLetters, Tone.valueOf(pinyinToneNumber));
	}

	private String formatDefinitions(final List<String> definitions) {
		final StringBuilder sb = new StringBuilder("/");
		for (final String definition : definitions) {
			sb.append(" ").append(definition.trim()).append(" /");
		}
		return sb.toString();
	}

	private List<String> unformatDefinitions(final String english) {
		final String[] definitions = english.substring(2, english.length() - 2).split(" / ");
		return Arrays.asList(definitions);
	}

	@Override
	public List<DictionaryEntry> findPinyin(final List<PinyinSyllable> pinyin, final long limit, final long offset,
			final SearchCanceller canceller) {
		if (limit < 0) {
			throw new IllegalArgumentException("Invalid limit: " + limit);
		}
		if (offset < 0) {
			throw new IllegalArgumentException("Invalid offset: " + offset);
		}
		LOGGER.debug("Finding pinyin: " + pinyin);
		final String pinyinQueryString = this.formatPinyinQuery(pinyin);
		final PreparedQuery<DbDictionaryEntry> query;
		try {
			query = this.dictionaryEntryDao.queryBuilder()
					.orderByRaw("case when like (?, " + DbDictionaryEntry.COLUMN_PINYIN + ") " +
								"then 1 else 0 end desc, " +
							"length(" + DbDictionaryEntry.COLUMN_HANZI_SIMPLIFIED + "), " +
							DbDictionaryEntry.COLUMN_PINYIN,
						new SelectArg(SqlType.STRING, "" + pinyinQueryString + " %"))
					.limit(limit)
					.offset(offset)
					.where().like(DbDictionaryEntry.COLUMN_PINYIN, new SelectArg(pinyinQueryString + "%"))
					.prepare();
		} catch (final SQLException e) {
			throw new DictionaryDataStoreException("Failed to create query", e);
		}
		return this.transformEntries(doQuery(query, canceller, pinyinQueryString));
	}

	private List<DictionaryEntry> transformEntries(final List<DbDictionaryEntry> dbEntries) {
		final List<DictionaryEntry> entries = new LinkedList<DictionaryEntry>();
		for (final DbDictionaryEntry dbEntry : dbEntries) {
			entries.add(this.createEntry(dbEntry));
		}
		return entries;
	}

	private DictionaryEntry createEntry(final DbDictionaryEntry dbEntry) {
		final String traditional = dbEntry.getTraditional();
		final String simplified = dbEntry.getSimplified();
		final String pinyin = dbEntry.getPinyin();
		final String english = dbEntry.getEnglish();
		return new DictionaryEntry(traditional, simplified, this.unformatPinyin(pinyin), this.unformatDefinitions(english));
	}

	@Override
	public List<DictionaryEntry> findChinese(final String chineseCharacters, final long limit, final long offset,
			final SearchCanceller canceller) {
		if (limit < 0) {
			throw new IllegalArgumentException("Invalid limit: " + limit);
		}
		if (offset < 0) {
			throw new IllegalArgumentException("Invalid offset: " + offset);
		}
		LOGGER.debug("Finding Chinese characters: " + chineseCharacters);
		final PreparedQuery<DbDictionaryEntry> query;
		try {
			query = this.dictionaryEntryDao.queryBuilder()
					.orderByRaw("case " +
							"when like (?, " + DbDictionaryEntry.COLUMN_HANZI_SIMPLIFIED + ") then 0 " +
							"else 1 end, " +
						"length(" + DbDictionaryEntry.COLUMN_HANZI_SIMPLIFIED + "), " +
						DbDictionaryEntry.COLUMN_PINYIN,
						new SelectArg(SqlType.STRING, chineseCharacters + "%"))
					.limit(limit)
					.offset(offset)
					.where().like(DbDictionaryEntry.COLUMN_HANZI_SIMPLIFIED, new SelectArg("%" + chineseCharacters + "%"))
					.prepare();
		} catch (final SQLException e) {
			throw new DictionaryDataStoreException("Failed to prepare query", e);
		}
		return this.transformEntries(doQuery(query, canceller, chineseCharacters));
	}

	@Override
	public List<DictionaryEntry> findDefinitions(final String englishWords, final long limit, final long offset,
			final SearchCanceller canceller) {
		if (limit < 0) {
			throw new IllegalArgumentException("Invalid limit: " + limit);
		}
		if (offset < 0) {
			throw new IllegalArgumentException("Invalid offset: " + offset);
		}
		LOGGER.debug("Finding definitions: " + englishWords);
		final PreparedQuery<DbDictionaryEntry> query;
		try {
			query = this.dictionaryEntryDao.queryBuilder()
					.orderByRaw("case " +
								"when like (?, " + DbDictionaryEntry.COLUMN_ENGLISH + ") then 0 " +
								"when like (?, " + DbDictionaryEntry.COLUMN_ENGLISH + ") then 1 " +
								"when like (?, " + DbDictionaryEntry.COLUMN_ENGLISH + ") then 2 " +
								"when like (?, " + DbDictionaryEntry.COLUMN_ENGLISH + ") then 3 " +
								"else 4 end, " +
							"length(" + DbDictionaryEntry.COLUMN_HANZI_SIMPLIFIED + "), " +
							DbDictionaryEntry.COLUMN_PINYIN,
						new SelectArg(SqlType.STRING, "/ " + englishWords + " /%"),
						new SelectArg(SqlType.STRING, "%/ " + englishWords + " /%"),
						new SelectArg(SqlType.STRING, "%/ " + englishWords + " %"),
						new SelectArg(SqlType.STRING, "% " + englishWords + " %"))
					.limit(limit)
					.offset(offset)
					.where().like(DbDictionaryEntry.COLUMN_ENGLISH, new SelectArg("%" + englishWords + "%"))
					.prepare();
		} catch (final SQLException e) {
			throw new DictionaryDataStoreException("Failed to create query", e);
		}
		return this.transformEntries(doQuery(query, canceller, englishWords));
	}

	private List<DbDictionaryEntry> doQuery(final PreparedQuery<DbDictionaryEntry> query, final SearchCanceller canceller, final String queryInput) {
		try {
			if (canceller != null) {
				return this.dictionaryEntryDao.query(query, createOrmliteSignaller(canceller));
			}
			return this.dictionaryEntryDao.query(query);
		} catch (final SQLException e) {
			if ("ORMLITE: query cancelled".equals(e.getMessage())) {
				throw new DictionaryDataStoreQueryCancelledException("Query cancelled: " + queryInput, e);
			}
			throw new DictionaryDataStoreException("Failed to execute query", e);
		}
	}

	private static CancellationSignaller createOrmliteSignaller(final SearchCanceller canceller) {
		final CancellationSignaller cancellationSignaller = new CancellationSignaller();
		canceller.register(new SearchCanceller.Listener() {
			@Override
			public void onCancel() {
				cancellationSignaller.signal();
			}
		});
		return cancellationSignaller;
	}

}
