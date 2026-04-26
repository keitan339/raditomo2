package com.raditomo.radiko.program;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

/**
 * ラジコ番組表XML（v3 program/date/{date}/{areaId}.xml）の Jackson XML マッピング。
 *
 * 構造例:
 * <pre>
 * &lt;radiko&gt;
 *   &lt;stations area_id="JP13"&gt;
 *     &lt;station id="TBS"&gt;
 *       &lt;name&gt;TBSラジオ&lt;/name&gt;
 *       &lt;progs&gt;
 *         &lt;prog ft="20260424050000" to="20260424080000" dur="10800"&gt;
 *           &lt;title&gt;...&lt;/title&gt;
 *           &lt;pfm&gt;...&lt;/pfm&gt;
 *           &lt;info&gt;...&lt;/info&gt;
 *           &lt;desc&gt;...&lt;/desc&gt;
 *           &lt;img&gt;...&lt;/img&gt;
 *         &lt;/prog&gt;
 *       &lt;/progs&gt;
 *     &lt;/station&gt;
 *   &lt;/stations&gt;
 * &lt;/radiko&gt;
 * </pre>
 */
public class ProgramXml {

    @JacksonXmlRootElement(localName = "radiko")
    public static class Root {
        @JacksonXmlProperty(localName = "stations")
        public Stations stations;
    }

    public static class Stations {
        @JacksonXmlProperty(isAttribute = true, localName = "area_id")
        public String areaId;

        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "station")
        public List<Station> stationList;
    }

    public static class Station {
        @JacksonXmlProperty(isAttribute = true, localName = "id")
        public String id;

        @JacksonXmlProperty(localName = "name")
        public String name;

        @JacksonXmlProperty(localName = "ascii_name")
        public String asciiName;

        @JacksonXmlProperty(localName = "logo")
        public String logo;

        @JacksonXmlProperty(localName = "progs")
        public Progs progs;
    }

    public static class Progs {
        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "prog")
        public List<Prog> progList;
    }

    public static class Prog {
        @JacksonXmlProperty(isAttribute = true, localName = "ft")
        public String ft;

        @JacksonXmlProperty(isAttribute = true, localName = "to")
        public String to;

        @JacksonXmlProperty(isAttribute = true, localName = "dur")
        public String dur;

        @JacksonXmlProperty(localName = "title")
        public String title;

        @JacksonXmlProperty(localName = "pfm")
        public String pfm;

        @JacksonXmlProperty(localName = "info")
        public String info;

        @JacksonXmlProperty(localName = "desc")
        public String desc;

        @JacksonXmlProperty(localName = "img")
        public String img;
    }
}
