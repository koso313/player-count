package com.playercount;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import lombok.Getter;

/** Today's peak and low OSRS total. Days roll over at 00:00 UTC (the game's daily reset). */
@Getter
final class DailyRecords
{
	private LocalDate day;
	private int peak = -1;
	private Instant peakAt;
	private int low = -1;
	private Instant lowAt;

	/** Returns true if anything changed (so the caller can save). */
	boolean update(int total, Instant now)
	{
		if (total <= 0)
		{
			return false;
		}
		LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
		if (!today.equals(day))
		{
			day = today;
			peak = low = total;
			peakAt = lowAt = now;
			return true;
		}
		boolean changed = false;
		if (total > peak)
		{
			peak = total;
			peakAt = now;
			changed = true;
		}
		if (total < low)
		{
			low = total;
			lowAt = now;
			changed = true;
		}
		return changed;
	}

	boolean hasData(Instant now)
	{
		return day != null && day.equals(now.atZone(ZoneOffset.UTC).toLocalDate());
	}

	String serialize()
	{
		if (day == null)
		{
			return "";
		}
		return day + "," + peak + "," + peakAt.getEpochSecond() + "," + low + "," + lowAt.getEpochSecond();
	}

	static DailyRecords parse(String s)
	{
		DailyRecords r = new DailyRecords();
		if (s == null || s.isEmpty())
		{
			return r;
		}
		try
		{
			String[] p = s.split(",");
			r.day = LocalDate.parse(p[0]);
			r.peak = Integer.parseInt(p[1]);
			r.peakAt = Instant.ofEpochSecond(Long.parseLong(p[2]));
			r.low = Integer.parseInt(p[3]);
			r.lowAt = Instant.ofEpochSecond(Long.parseLong(p[4]));
		}
		catch (RuntimeException e)
		{
			return new DailyRecords();
		}
		return r;
	}
}
