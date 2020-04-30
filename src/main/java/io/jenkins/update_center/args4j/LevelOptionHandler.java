package io.jenkins.update_center.args4j;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OneArgumentOptionHandler;
import org.kohsuke.args4j.spi.Setter;

import java.util.logging.Level;

public class LevelOptionHandler extends OneArgumentOptionHandler<Level> {
    public LevelOptionHandler(CmdLineParser parser, OptionDef option, Setter<? super Level> setter) {
        super(parser, option, setter);
    }

    @Override
    public String getDefaultMetaVariable() {
        return "LEVEL";
    }

    @Override
    protected Level parse(String s) throws NumberFormatException, CmdLineException {
        return Level.parse(s);
    }
}
