/*
 * The MIT License
 *
 * Copyright (c) 2020, Daniel Beck
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.jenkins.update_center.json;

import com.alibaba.fastjson.annotation.JSONField;
import hudson.util.VersionNumber;
import io.jenkins.update_center.HPI;
import io.jenkins.update_center.JenkinsWar;
import io.jenkins.update_center.MavenRepository;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class TieredUpdateSitesGenerator extends WithoutSignature {

    private MavenRepository repository;

    @JSONField
    public List<String> weeklyCores;

    @JSONField
    public List<String> stableCores;

    public TieredUpdateSitesGenerator withRepository(MavenRepository repository) throws IOException {
        this.repository = repository;
        update();
        return this;
    }

    private static boolean isStableVersion(VersionNumber version) {
        return version.getDigitAt(2) != -1;
    }

    private static VersionNumber nextWeeklyReleaseAfterStableBaseline(VersionNumber version) {
        if (!version.toString().matches("[0-9][.][0-9]+[.][1-9]")) {
            throw new IllegalArgumentException("Unexpected LTS version: " + version.toString());
        }
        return new VersionNumber(version.getDigitAt(0) + "." + (version.getDigitAt(1) + 1));
    }

    private VersionNumber nextLtsReleaseAfterWeekly(VersionNumber dependencyVersion, Set<VersionNumber> keySet) {
        return keySet.stream().filter(TieredUpdateSitesGenerator::isStableVersion).sorted().filter(v -> v.isNewerThan(dependencyVersion)).findFirst().orElse(null);
    }

    private static boolean isReleaseRecentEnough(JenkinsWar war) throws IOException {
        Objects.requireNonNull(war, "war");
        return war.getTimestampAsDate().toInstant().isAfter(Instant.now().minus(CORE_AGE_DAYS, ChronoUnit.DAYS));
    }

    public void update() throws IOException {
        Collection<HPI> allPluginReleases = this.repository.listJenkinsPlugins().stream()
                .map(plugin -> plugin.getArtifacts().values())
                .reduce(new HashSet<>(), (acc, els) -> { acc.addAll(els); return acc; });

        final List<VersionNumber> coreDependencyVersions = allPluginReleases.stream().map(v -> {
            try {
                return v.getRequiredJenkinsVersion();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to determine required Jenkins version for " + v.getGavId(), e);
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toSet()).stream().map(VersionNumber::new).sorted(Comparator.reverseOrder()).collect(Collectors.toList());

        final TreeMap<VersionNumber, JenkinsWar> allJenkinsWarsByVersionNumber = this.repository.getJenkinsWarsByVersionNumber();
        final Set<VersionNumber> weeklyCores = new HashSet<>();
        final Set<VersionNumber> stableCores = new HashSet<>();

        boolean stableDone = false;
        boolean weeklyDone = false;

        for (VersionNumber dependencyVersion : coreDependencyVersions) {
            final JenkinsWar war = allJenkinsWarsByVersionNumber.get(dependencyVersion);
            if (war == null) {
                LOGGER.log(Level.INFO, "Did not find declared core dependency version among all core releases: " + dependencyVersion.toString() + ". It is used by " + allPluginReleases.stream().filter( p -> {
                    try {
                        return p.getRequiredJenkinsVersion().equals(dependencyVersion.toString());
                    } catch (IOException e) {
                        // ignore
                        return false;
                    }
                }).map(HPI::getGavId).collect(Collectors.joining(", ")));
                continue;
            }
            final boolean releaseRecentEnough = isReleaseRecentEnough(war);
            if (isStableVersion(dependencyVersion)) {
                if (!stableDone) {
                    if (!releaseRecentEnough) {
                        stableDone = true;
                    }
                    stableCores.add(dependencyVersion);
                    if (!weeklyDone) {
                        weeklyCores.add(nextWeeklyReleaseAfterStableBaseline(dependencyVersion));
                    }
                }
            } else {
                if (!weeklyDone) {
                    if (!releaseRecentEnough) {
                        weeklyDone = true;
                    }
                    weeklyCores.add(dependencyVersion);
                }
                // Plugin depends on a weekly version, make sure the next higher LTS release is also included
                if (!stableDone) {
                    final VersionNumber v = nextLtsReleaseAfterWeekly(dependencyVersion, allJenkinsWarsByVersionNumber.keySet());
                    if (v != null) {
                        stableCores.add(v);
                    }
                }
            }
            if (stableDone && weeklyDone) {
                break;
            }
        }

        this.stableCores = stableCores.stream().map(VersionNumber::toString).sorted().collect(Collectors.toList());
        this.weeklyCores = weeklyCores.stream().map(VersionNumber::toString).sorted().collect(Collectors.toList());
    }

    public static final Logger LOGGER = Logger.getLogger(TieredUpdateSitesGenerator.class.getName());

    private static final int CORE_AGE_DAYS = 400;
}
