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

include("rules.php");

$out = "current";
foreach(array_keys($rules) as $r) {
  if (compare_version($v, $r)<=0) {
    $out = $rules[$r];
    break;
  }
}

if (array_key_exists('HTTPS', $_SERVER)) {
  $host = 'https://updates.jenkins-ci.org/';
} else {
  $host = 'http://mirrors.jenkins-ci.org/updates/';
}

header('Location: ' . $host . $out . '/' . $_GET['path'] )
?>
