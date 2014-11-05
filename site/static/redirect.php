<?php
# take a version number like "1.580.1-SNAPSHOT" and divide it up to an int array like [1,580,1,-1].
function parse($v) {
  $v = preg_split("/[.-]/",$v);
  for ($i=0; $i<sizeof($v); $i++) {
    if ($v[$i]=="SNAPSHOT") $v[$i]="-1";
    $v[$i] = intval($v[$i]);
  }
  return $v;
}

# compare two version number strings
function compare_version($lhs,$rhs) {
  $l = parse($lhs);
  $r = parse($rhs);
  $ls = sizeof($l);
  $rs = sizeof($r);

  for ($i=0; $i<max($ls,$rs); $i++) {
    $ll = $i<$ls ? $l[$i] : 0;
    $rr = $i<$rs ? $r[$i] : 0;
    if ($ll < $rr)	return -1;
    if ($ll > $rr)	return 1;
  }
  return 0;
}

$v=$_GET['version'];

$out = "1.x";
foreach(glob("*/cap.txt") as $line) {
  $target = chop(file_get_contents($line));
  if (compare_version($v, $target.".999")<0) {
    $out = dirname($line);
    break;
  }
}

#$targets = file("versions.txt", FILE_IGNORE_NEW_LINES|FILE_SKIP_EMPTY_LINES);
#$out = "latest";
#foreach ($targets as $target) {
#  if (compare_version($v, $target.".999")<0) {
#    $out = $target;
#    break;
#  }
#}

if ($_SERVER['HTTPS']) {
  $host = 'https://updates.jenkins-ci.org/';
} else {
  $host = 'http://mirrors.jenkins-ci.org/';
}

header('Location: ' . $host . $out . '/' . $_GET['path'] )
?>
