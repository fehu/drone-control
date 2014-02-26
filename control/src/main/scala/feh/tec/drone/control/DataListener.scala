package feh.tec.drone.control

/** Listens to data, obtained from IO channel(s)
 */
trait DataListener[Data]{

}

/** Analyses data to advise the decider (or other analyzers)
 */
trait Analyzer[Data] extends DataListener[Data]{

}

/** Watches execution of a process and notifies the decider on completion and problems
 */
trait Watcher[Data] extends DataListener[Data]
{

}

/** Notifies the decider about certain data constraints violations, intended to be used for danger detection
 */
trait Guardian[Data] extends DataListener[Data]{

}