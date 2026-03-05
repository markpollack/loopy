///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21
//REPOS central=https://repo1.maven.org/maven2/
//REPOS central-snapshots=https://central.sonatype.com/repository/maven-snapshots/
//DEPS io.github.markpollack:loopy:0.1.0-SNAPSHOT

import io.github.markpollack.loopy.LoopyApp;

public class loopy {

	public static void main(String[] args) {
		LoopyApp.main(args);
	}

}
