package com.playercount;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import lombok.extern.slf4j.Slf4j;

/** Small gzipped files under ~/.runelite/player-count/. Writes go to a temp file first so a crash can't corrupt them. */
@Slf4j
final class DataStore
{
	interface Writer
	{
		void write(DataOutputStream out) throws IOException;
	}

	interface Reader
	{
		void read(DataInputStream in) throws IOException;
	}

	private final File dir;

	DataStore(File dir)
	{
		this.dir = dir;
	}

	void save(String name, Writer writer)
	{
		try
		{
			Files.createDirectories(dir.toPath());
			File tmp = new File(dir, name + ".tmp");
			try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)))))
			{
				writer.write(out);
			}
			Files.move(tmp.toPath(), new File(dir, name).toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
		catch (IOException e)
		{
			log.warn("Couldn't save {}", name, e);
		}
	}

	void load(String name, Reader reader)
	{
		File f = new File(dir, name);
		if (!f.isFile())
		{
			return;
		}
		try (DataInputStream in = new DataInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(f)))))
		{
			reader.read(in);
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Couldn't load {}, starting fresh", name, e);
		}
	}
}
