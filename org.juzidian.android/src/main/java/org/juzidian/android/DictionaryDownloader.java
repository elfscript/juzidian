/*
 * Copyright Nathan Jones 2013
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
package org.juzidian.android;

import javax.inject.Inject;

import org.juzidian.core.dataload.DictionaryResource;
import org.juzidian.core.dataload.DictionaryResourceRegistry;
import org.juzidian.core.dataload.DictionaryResourceRegistryService;
import org.juzidian.core.dataload.DictonaryResourceRegistryServiceException;
import org.juzidian.core.datastore.DbDictionaryDataStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds a compatible dictionary and then schedules its download.
 */
public class DictionaryDownloader {

	private static final Logger LOGGER = LoggerFactory.getLogger(DictionaryDownloader.class);

	private final DictionaryResourceRegistryService registryService;

	private final JuzidianDownloadManager downloadManager;

	@Inject
	public DictionaryDownloader(final JuzidianDownloadManager downloadManager, final DictionaryResourceRegistryService registryService) {
		this.downloadManager = downloadManager;
		this.registryService = registryService;
	}

	public void downloadDictionary() {
		LOGGER.debug("Initializing download of dictionary database.");
		final DictionaryResource dictionaryResource = this.getDictionaryResource();
		final String url = dictionaryResource.getUrl();
		this.downloadManager.startDownload(url);
	}

	private DictionaryResource getDictionaryResource() {
		final DictionaryResourceRegistry registry;
		try {
			registry = this.registryService.getDictionaryResourceRegistry(DbDictionaryDataStore.DATA_FORMAT_VERSION);
		} catch (final DictonaryResourceRegistryServiceException e) {
			throw new RuntimeException(e);
		}
		return registry.getDictionaryResources().get(0);
	}

}