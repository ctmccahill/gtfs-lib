package com.conveyal.gtfs.loader;

/**
 * Created by abyrd on 2017-03-30
 */
public enum Requirement {
    REQUIRED,  // Required by the GTFS spec
    OPTIONAL,  // Optional according to the GTFS spec
    EXTENSION, // Extension proposed and documented on gtfs-changes
    UNKNOWN    // Undocumented proprietary extension
}
