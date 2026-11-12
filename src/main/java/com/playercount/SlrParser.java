package com.playercount;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldRegion;
import net.runelite.http.api.worlds.WorldType;

/**
 * Parses Jagex's live world list (slr.ws), the same file the in-game world switcher reads.
 * RuneLite's own copy of this list is cached for 10 minutes; this one isn't cached.
 *
 * Layout (big-endian): int length, ushort worldCount, then per world:
 * ushort id, int typeFlags, cstring address, cstring activity, ubyte location, short players.
 */
final class SlrParser
{
	static final String URL = "https://www.runescape.com/g=oldscape/slr.ws?order=LPWM";

	private static final Charset CP1252 = Charset.forName("windows-1252");

	private SlrParser()
	{
	}

	static List<World> parse(byte[] data)
	{
		try
		{
			ByteBuffer buf = ByteBuffer.wrap(data);
			buf.getInt(); // payload length
			int count = buf.getShort() & 0xFFFF;
			List<World> worlds = new ArrayList<>(count);
			for (int i = 0; i < count; i++)
			{
				int id = buf.getShort() & 0xFFFF;
				int flags = buf.getInt();
				String address = readString(buf);
				String activity = readString(buf);
				int location = buf.get() & 0xFF;
				int players = buf.getShort();
				worlds.add(World.builder()
					.id(id)
					.types(toTypes(flags))
					.address(address)
					.activity(activity)
					.region(WorldRegion.fromLocationId(location))
					.players(players)
					.build());
			}
			return worlds;
		}
		catch (BufferUnderflowException e)
		{
			throw new IllegalArgumentException("Truncated world list", e);
		}
	}

	private static String readString(ByteBuffer buf)
	{
		int start = buf.position();
		while (buf.get() != 0)
		{
			// scan to terminator
		}
		int len = buf.position() - start - 1;
		return new String(buf.array(), start, len, CP1252);
	}

	/** Client flag bits to the http-api enum, matched by name (the two enums mostly share names). */
	static EnumSet<WorldType> toTypes(int flags)
	{
		EnumSet<WorldType> out = EnumSet.noneOf(WorldType.class);
		for (net.runelite.api.WorldType t : net.runelite.api.WorldType.fromMask(flags))
		{
			String name = t == net.runelite.api.WorldType.TOURNAMENT_WORLD ? "TOURNAMENT" : t.name();
			try
			{
				out.add(WorldType.valueOf(name));
			}
			catch (IllegalArgumentException ignored)
			{
				// no http-api equivalent (e.g. HARDCORE_MAIN); not needed for filtering
			}
		}
		return out;
	}
}
