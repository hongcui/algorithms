#this version is a simplified version of unsuperviseClauseMarkupBenchmark.pl
#the only goal the program achieves is extracting organ names with high accuracy (faster speed too)
#in Nounheuristics, do not extract nounendings such as -tion, -sure.
#removed all additional modules dealing with adjSubject, compound subject, phrase clause etc.
package unsupervisedOrganNameExtraction;
use lib '..\\paragraphExtraction\\UnsupervisedClauseMarkup\\';
use NounHeuristics;
use SentenceSpliter;
use ReadFile;
use strict;
use DBI;

my $debug = 0;
my $debugp = 0; #debug pattern
	my $kb = "knowledgebase";
	my $taglength = 150;

	my $host = "localhost";
	my $user = "termsuser";
	my $password = "termspassword";
	my $dbh = DBI->connect("DBI:mysql:host=$host", $user, $password)
	or die DBI->errstr."\n";
	
	my $CHECKEDWORDS = ":"; #leading three words of sentences
	my $N = 3; #$N leading words
	my $SENTID = 0;
	my $DECISIONID = 0;
	my $PROPERNOUNS = "propernouns"; #EOL
	my %WNNUMBER =(); #word->(p|s)
	my %WNSINGULAR = ();#word->singular
	my %WNPOS = ();   #word->POSs
	my %WNPOSRECORDS = ();
	my $NEWDESCRIPTION =""; #record the index of sentences that ends a description
	my %WORDS = ();
	my %PLURALS = ();
	#3/12/09
	#my %NUMBERS = (0, 'zero', 1, 'one', 'first',2, 'two','second', 3, 'three','third', 'thirds',4,'four','fourth','fourths', 5,'five','fifth','fifths', 6,'six','sixth','sixths',7,'seven','seventh','sevenths', 8,'eight','eighths','eighth',9,'nine','ninths','ninth','tenths','tenth');
	#4/22/09
	our $NUMBERS = "zero|one|ones|first|two|second|three|third|thirds|four|fourth|fourths|quarter|five|fifth|fifths|six|sixth|sixths|seven|seventh|sevenths|eight|eighths|eighth|nine|ninths|ninth|tenths|tenth";
	#the following two patterns are used in mySQL rlike
	my $PREFIX ="ab|ad|bi|deca|de|dis|di|dodeca|endo|end|e|hemi|hetero|hexa|homo|infra|inter|ir|macro|mega|meso|micro|mid|mono|multi|ob|octo|over|penta|poly|postero|post|ptero|pseudo|quadri|quinque|semi|sub|sur|syn|tetra|tri|uni|un|xero|[a-z0-9]+_";
	my $SUFFIX ="er|est|fid|form|ish|less|like|ly|merous|most|shaped"; # 3_nerved, )_nerved, dealt with in subroutine
	#my $FORBIDDEN ="to|and|or|nor"; #words in this list can not be treated as boundaries "to|a|b" etc.
	my $FORBIDDEN ="xxxxxxxxxxxxxx|yyyyyyyyyyyyyyyyy"; #disable $FORBIDDEN for unsupervisedOrganNameExtraction.pm.
	our $PRONOUN ="all|each|every|some|few|individual|both|other";
	our $CHARACTER ="lengths|length|lengthed|width|widths|widthed|heights|height|character|characters|distribution|distributions|outline|outlines|profile|profiles|feature|features|form|forms|mechanism|mechanisms|nature|natures|shape|shapes|shaped|size|sizes|sized";#remove growth, for growth line. check 207, 3971
	our $PROPOSITION ="above|across|after|along|around|as|at|before|beneath|between|beyond|by|for|from|in|into|near|of|off|on|onto|out|outside|over|than|throughout|toward|towards|up|upward|with|without";
	my $TAGS = "";
	my $PLENDINGS = "[^aeiou]ies|i|ia|(x|ch|sh)es|ves|ices|ae|s";
	our $CLUSTERSTRINGS = "group|groups|clusters|cluster|arrays|array|series|fascicles|fascicle|pairs|pair|rows|number|numbers|\\d+";
	our $SUBSTRUCTURESTRINGS = "part|parts|area|areas|portion|portions";
		
	my $mptn = "((?:[mbq][,&]*)*(?:m|b|q(?=[pon])))";#grouped #may contain q but not the last m, unless it is followed by a p
	my $nptn = "((?:[nop][,&]*)*[nop])"; #grouped #must present, no q allowed
	my $bptn = "([,;:\\.]*\$|,*[bm]|(?<=[pon]),*q)"; #grouped #when following a p, a b could be a q
	my $SEGANDORPTN = "(?:".$mptn."?".$nptn.")"; #((?:[mq],?)*&?(?:m|q(?=p))?)((?:[np],?)*&?[np])
	my $ANDORPTN = "^(?:".$SEGANDORPTN."[,&]+)*".$SEGANDORPTN.$bptn;
	
	my $IGNOREPTN = "(assignment|resemb[a-z]+|like [A-Z]|similar|differs|differ|revision|genus|family|suborder|species|specimen|order|superfamily|class|known|characters|characteristics|prepared|subphylum|assign[a-z]*|available|nomen dubium|said|topotype|1[5-9][0-9][0-9])";
		 
	our $stop = $NounHeuristics::STOP;
	my $dataprefix = "";
	


sub extractOrganNames{
	my ($db, $mode, $pref, %paragraphs) = @_;

	$dataprefix = $pref;
	print stdout "Initialized:\n";
	###########################################################################################
	#########################                                     #############################
	#########################set up global variables              #############################
	###########################################################################################
	
	#prepare database
	my $haskb = kbexists();
	$haskb = 0;
	setupdatabase($db);
	
	if($haskb){
		importfromkb();
	}
	
	#read sentences in from disk	
	print stdout "Reading sentences:\n";
	populatesents(%paragraphs);
	
	my $text = "";
	while( my ($k, $v) = each %paragraphs ) {
        $text .= $v." ";
	}
	addheuristicsnouns($text);
	addstopwords();
	addcharacters();#3/24/09
	addnumbers(); #4/11/09
	addclusterstrings(); #4/11/09
	addpropernouns(); #6/2/09
	
	posbysuffix();
	resetcounts();
	###############################################################################
	# bootstrap between %NOUNS and %BDRY on plain text description
	# B: a boundary word, N: a noun or noun *phrase* ?: a unknown word
	# goal: grow %NOUNS and %BDRY + confirm tags
	#
	# decision table: leading three words (@todo: exclude "at the center of xxx")
	# foremost, collect the patterns and find the number of unique instances (I) of "?"
	# deal with the cases with good hints first
	# leave the cases with high uncertainty for the next iteration
	# use the NNP as the tag "flower buds" "basal leaves"
	# with the assumption that "N N N"s are rare, "N N" and "N" are most common
	
	# clues: ?~pl, tag words' POS patterns
	# N <=>B [last N (likely to be a pl) is followed by a B, the first B is proceeded by a N]
	# need to distinguish the two usages of Ns, [1] when used as the main N in a tag e.g. <female flowers>
	# [2] when used to modify another N in a tag <flower buds>
	# if [1] is seen, then <flower> shouldn't be a tag.
	# cases: see *rulebasedlearn*  for heuritics
	# 1  N N N => make "N N N" the tag
	# 2    N B => make "N N" the tag
	# 3    N ? => if I>2 or "N N" is a phrase or N2-pl, ? -> %BDRY and make "N N" the tag
	# 4    B N => make "N1" the tag
	# 5    B B => make N the tag
	# 6    B ? => make N the tag
	# 7    ? N => if I>2 or N1-pl, ? -> %BDRY and make N1 the tag; else @nextiteration
	# 8    ? B => if N-pl, ? -> %BDRY and make N the tag; if I>2 and N1-sg and ?~pl, ? -> %NOUNS and make "N ?" the tag; else @nextiteration
	# 9    ? ? => if N-pl, ? -> %BDRY and make N the tag; if any ?~pl, make up to the ? the tag and that ?->@NOUNS; else @nextiteration
	# 10 B N N => make "B N N" the tag
	# 11   N B => make "B N" the tag
	# 12   N ? => if N-pl, ? -> %BDRY and make "B N" the tag; if ?~pl, make "B N ?" the tag and ?->@NOUNS;else @nextiteration
	# 13   B N => make "B B N" the tag
	# 14   B B => search for the first N and make it the tag
	# 15   B ? => if I>2, ?-> %NOUNS and make "B B N" the tag; else @nextiteration
	# 16   ? N => make "B ? N" the tag
	# 17   ? B => ? -> %NOUNS and make "B1 ?" the tag.
	# 18   ? ? => @nextiteration
	# 19 ? N N => if I>2 or any N-pl, make up to the N the tag and ? ->%BDRY
	# 20   N B => make "? N" the tag
	# 21   N ? => make N the tag and ?2->%BDRY
	# 22   B N => make "? B N" the tag
	# 23   B B => ? -> %NOUNS
	# 24   B ? => ?1 -> %NOUNS
	# 25   ? N => make N the tag @nextiteration
	# 26   ? B => @nextiteration
	# 27   ? ? => @nextiteration
	################################################################################
	# Attach sequential numbers to sentences 1,2,3,..., n.
	# Take a sentence,
	#       Collect:
	#           POSs for the first 3 words of the sentence: W1/P W2/P W3/P
	#           All sentences with not checked W[1-3] as their first 3 words.
	#           Add W[1-3] to the checked words list
	#       Do:
	#           Use rulebasedlearn(doit) on cases 1-27 and clues to grow %NOUNS and %BDRY and attach tags to the end of sentences.
	#           [@todo:If in conflict with previous decisions, merge the two sets of
	#                                       sentences, then apply rules and clues]
	#           Use instancebaselearn on the remaining sentences
	#           Index these sentences with the decision number
	#
	#           GO TO: Take a sentence
	#       Stop:
	#           When no new term is entered to %NOUNS and %BDRY
	#       Markup the remaining sentences with default tags
	#       Dump marked sentences to disk
	###############################################################################
	
	
	markupbypattern(); #chromosome
	
	markupignore();#similar to , differs from etc.3/21/09
		
	print stdout "Learning rules with high certainty:\n";
	discover("start");
	
	print stdout "Bootstrapping rules:\n";
	discover("normal");
	
	separatemodifiertag(); 
	print stdout "::::::::::::::::::::::::Final step: normalize tag and modifiers: \n";
	normalizetags(); ##normalization is the last step : turn all tags and modifiers to singular fo
	
	print stdout "Done:\n";
	
}
###################################################################################################
###################### managing KB                             ####################################
###################################################################################################
sub kbexists{

my $test = $dbh->prepare('show databases') or die $dbh->errstr."\n";
$test->execute() or die $test->errstr."\n";
my ($database, $removedb);
while($database = $test->fetchrow_array()){
	if($database eq $kb){
		return 1;
	}
}
return 0;
}

sub importfromkb{
    #import from learnedboundary and learnedstates to "b"
    #impprt from learnedstructures to "n"
    #forbidden word to "f"
	my ($stmt1, $sth1, $stmt2, $sth2, $w, @forbid);
	
	@forbid = split(/\|/, $FORBIDDEN);
	foreach (@forbid){
		$stmt2 ="insert into ".$dataprefix."_wordpos values(\"$_\",\"f\",\"\",1,1)";
		$sth2 = $dbh->prepare($stmt2);
		$sth2->execute();
	}
	
	
	$stmt1 = "select distinct word from ".$kb.".learnedboundarywords where word !='' and not isnull(word)";
	$sth1 = $dbh->prepare($stmt1);
	$sth1->execute() or die $sth1->errstr."\n";
	while($w = $sth1->fetchrow_array()){
		if($w !~ /\w/ || $w =~/\b(?:$FORBIDDEN)\b/){next;}
		$stmt2 ="insert into ".$dataprefix."_wordpos values(\"$w\",\"b\",\"\",1,1)";
		$sth2 = $dbh->prepare($stmt2);
		$sth2->execute();
	}
	
	$stmt1 = "select distinct modifier from ".$kb.".learnedmodifiers where modifier !='' and not isnull(modifier)";
	$sth1 = $dbh->prepare($stmt1);
	$sth1->execute() or die $sth1->errstr."\n";
	while($w = $sth1->fetchrow_array()){
		if($w !~ /\w/ || $w =~/\b(?:$FORBIDDEN)\b/){next;}
		$stmt2 ="insert into modifiers values('$w',1,1)";
		$sth2 = $dbh->prepare($stmt2);
		$sth2->execute();
	}
	
	#$stmt1 = "select distinct state from ".$kb.".learnedstates where state !='' and not isnull(state)";
	#$sth1 = $dbh->prepare($stmt1);
	#$sth1->execute() or die $sth1->errstr."\n";
	#while($w = $sth1->fetchrow_array()){
	#	if($w !~ /\w/ || $w =~/\b(?:$FORBIDDEN)\b/){next;}
	#	$stmt2 ="insert into ".$dataprefix."_wordpos values(\"$w\",\"b\",\"\",1,1)";
	#	$sth2 = $dbh->prepare($stmt2);
	#	$sth2->execute();
	#}
	
	$stmt1 = "select distinct structure from ".$kb.".learnedstructures where structure !='' and not isnull(structure)";
	$sth1 = $dbh->prepare($stmt1);
	$sth1->execute() or die $sth1->errstr."\n";
	while($w = $sth1->fetchrow_array()){
		if($w !~ /\w/ || $w =~/\b(?:$FORBIDDEN)\b/){next;}
		$stmt2 ="insert into ".$dataprefix."_wordpos values(\"$w\",\"n\",\"\",1,1)";
		$sth2 = $dbh->prepare($stmt2);
		$sth2->execute();
	}
}

############################################################################################
########################prepare database tables            #################################
############################################################################################
sub setupdatabase{
	my $db = shift;

#my $test = $dbh->prepare('show databases')
#or die $dbh->errstr."\n";
#$test->execute() or die $test->errstr."\n";
#my ($database, $removedb);
#while($database = $test->fetchrow_array()){
#if($database eq $db){
#$removedb = 1;
#last;
#}
#}

#if($removedb){
#my $test = $dbh->prepare('drop database '.$db)
#or die $dbh->errstr."\n";
#$test->execute() or die $test->errstr."\n";
#}

my $test = $dbh->prepare('create database if not exists '.$db.' CHARACTER SET utf8')
or die $dbh->errstr."\n";
$test->execute() or die $test->errstr."\n";

my $test = $dbh->prepare('use '.$db)
or die $dbh->errstr."\n";

$test->execute() or die $test->errstr."\n";

my ($create, $del);

$create = $dbh->prepare('create table if not exists '.$dataprefix.'_sentence (sentid int(11) not null unique, source varchar(500), sentence text, originalsent text, lead varchar(50), status varchar(20), tag varchar('.$taglength.'),modifier varchar(150), charsegment varchar(500),primary key (sentid)) engine=innodb');
$create->execute() or warn "$create->errstr\n";
$del = $dbh->prepare('delete from '.$dataprefix.'_sentence');
$del->execute();


$create = $dbh->prepare('create table if not exists '.$dataprefix.'_wordpos (word varchar(50) not null, pos varchar(2) not null, role varchar(5), certaintyu int, certaintyl int, primary key (word, pos)) engine=innodb');
$create->execute() or warn "$create->errstr\n";
$del = $dbh->prepare('delete from '.$dataprefix.'_wordpos');
$del->execute();

$create = $dbh->prepare('create table if not exists '.$dataprefix.'_sentInFile (filename varchar(200) not null unique primary key, endindex int not null) engine=innodb');
$create->execute() or warn "$create->errstr\n";
$del = $dbh->prepare('delete from '.$dataprefix.'_sentInFile');
$del->execute();

$create = $dbh->prepare('create table if not exists '.$dataprefix.'_modifiers (word varchar(50) not null unique primary key, count int, istypemodifier tinyint) engine=innodb');
$create->execute() or warn "$create->errstr\n";
$del = $dbh->prepare('delete from '.$dataprefix.'_modifiers');
$del->execute();

$create = $dbh->prepare('create table if not exists '.$dataprefix.'_isA (autoid int not null auto_increment primary key, instance varchar(50), class varchar(50)) engine=innodb');
$create->execute() or warn "$create->errstr\n";
$del = $dbh->prepare('delete from '.$dataprefix.'_isA');
$del->execute();

$create = $dbh->prepare('create table if not exists '.$dataprefix.'_unknownwords (word varchar(50) not null primary key, flag varchar(50)) engine=innodb');
$create->execute() or warn "$create->errstr\n";
$del = $dbh->prepare('delete from '.$dataprefix.'_unknownwords');
$del->execute();


$create = $dbh->prepare('create table if not exists '.$dataprefix.'_singularplural (singular varchar(50), plural varchar(50), primary key (singular, plural)) engine=innodb');
$create->execute() or warn "$create->errstr\n";
#$del = $dbh->prepare('delete from '.$dataprefix.'_singularplural');
#$del->execute();

$create = $dbh->prepare('create table if not exists '.$dataprefix.'_discounted (word varchar(50), discountedpos varchar(5), possiblenewpos varchar(5), primary key (word, discountedpos)) engine=innodb');
$create->execute() or warn "$create->errstr\n";
$del = $dbh->prepare('delete from '.$dataprefix.'_discounted');
$del->execute();

$create = $dbh->prepare('create table if not exists '.$dataprefix.'_substructure (structure varchar(100), substructure varchar(100), count int, primary key (structure, substructure)) engine=innodb');
$create->execute() or warn "$create->errstr\n";
$del = $dbh->prepare('delete from '.$dataprefix.'_substructure');
$del->execute();
}


############################################################################################
########################populate wordpos by heuristics     #################################
############################################################################################

#use prefix to learn about unknown words
#input: a word and its pos tag, plus the destination table [nspb =>wordpos; m=>modifiers]
#remove from the unknownwords table any word that can be identified by adding or removing prefix from the known words
#insert newly discovered words in corresponding table
#return the number of new discoveries

sub addstopwords{
	#$stop =~ s#\|(and|or)\|#|#g; #and|or in $FORBIDDEN
	my @stops = split(/\|/,$stop);
	push(@stops, "NUM", "(", "[", "{", ")", "]", "}");
	
	push(@stops, "\\\\d+"); 
	print "stop list:\n@stops\n" if $debug;
	
	for (@stops){
		my $w = $_;
		if($w =~/\b(?:$FORBIDDEN)\b/){next;}
		update($w, "b", "*", "wordpos", 0); 
		#my $stmt ="insert into ".$dataprefix."_wordpos values(\"$w\",\"b\",\"\",1,1)";
		#my $sth = $dbh->prepare($stmt);
		#$sth->execute();
	}
}

sub addpropernouns{
	my @chars = split(/\|/,$PROPERNOUNS);
	
	print "proper nouns:\n$PROPERNOUNS\n" if $debug;
	
	for (@chars){
		my $w = $_;
		if($w =~/\b(?:$FORBIDDEN)\b/){next;}
		update($w, "z", "*", "wordpos", 0); 
	}
}

sub addcharacters{
	my @chars = split(/\|/,$CHARACTER);
	
	print "character list:\n$CHARACTER\n" if $debug;
	
	for (@chars){
		my $w = $_;
		if($w =~/\b(?:$FORBIDDEN)\b/){next;}
		update($w, "b", "*", "wordpos", 0); 
	}
}

sub addnumbers{
	my @chars = split(/\|/,$NUMBERS);
	
	print "numbers:\n$NUMBERS\n" if $debug;
	
	for (@chars){
		my $w = $_;
		if($w =~/\b(?:$FORBIDDEN)\b/){next;}
		update($w, "b", "*", "wordpos", 0); 
	}
	update("NUM", "b", "*", "wordpos", 0); 
}

sub addclusterstrings{
	my @chars = split(/\|/,$CLUSTERSTRINGS);
	
	print "clusterstrings :\n$CLUSTERSTRINGS\n" if $debug;
	
	for (@chars){
		my $w = $_;
		if($w =~/\b(?:$FORBIDDEN)\b/){next;}
		update($w, "b", "*", "wordpos", 0); 
	}
}

###lateral/laterals terminal/terminals: blades of mid cauline spatulate or oblong to obovate or lanceolate , 6 � 35 � 1 � 15 cm , bases auriculate , auricles deltate to lanceolate , � straight , acute , margins usually pinnately lobed , lobes � deltate to lanceolate , not constricted at bases , terminals usually larger than laterals , entire or dentate .
sub addheuristicsnouns{
	my $text = shift;
	my @nouns = NounHeuristics::heurnouns($text, "");
	#EOL:@nouns = ("angle[s]", "angles[p]", "base[s]", "bases[p]", "cell[s]", "cells[p]", "depression[s]", "depressions[p]", "ellipsoid[s]", "ellipsoids[p]", "eyespot[s]", "eyespots[p]", "face[s]", "faces[p]", "flagellum[s]", "flagella[p]", "flange[s]", "flanges[p]", "globule[s]", "globules[p]", "groove[s]", "grooves[p]", "line[s]", "lines[p]", "lobe[s]", "lobes[p]", "margin[s]", "margins[p]", "membrane[s]", "membranes[p]", "notch[s]", "notches[p]", "plastid[s]", "plastids[p]", "pore[s]", "pores[p]", "pyrenoid[s]", "pyrenoids[p]", "quarter[s]", "quarters[p]", "ridge[s]", "ridges[p]", "rod[s]", "rods[p]", "row[s]", "rows[p]", "sample[s]", "samples[p]", "sediment[s]", "sediments[p]", "side[s]", "sides[p]", "vacuole[s]", "vacuoles[p]", "valve[s]", "valves[p]");
	print  "nouns learnt from heuristics:\n@nouns\n" if $debug;

	#"adhere[s] adheres[p] angle[s] angles[p] attach[s] attaches[p] base[s] bases[p] cell[s] cells[p] depression[s] depressions[p] direction[s] directions[p] ellipsoid[s] ellipsoids[p] eyespot[s] eyespots[p] face[s] faces[p] flagellum[s] flagella[p] flange[s] flanges[p] forward[s] forwards[p] globule[s] globules[p] groove[s] grooves[p] insert[s] inserts[p] jerk[s] jerks[p] length[s] lengths[p] lie[s] lies[p] line[s] lines[p] lobe[s] lobes[p] margin[s] margins[p] measure[s] meet[s] meets[p] membrane[s] membranes[p] narrow[s] narrows[p] notch[s] notches[p] observation[s] observations[p] plastid[s] plastids[p] pore[s] pores[p] pyrenoid[s] pyrenoids[p] quarter[s] quarters[p] ridge[s] ridges[p] rod[s] rods[p] row[s] rows[p] sample[s] samples[p] sediment[s] sediments[p] side[s] sides[p] size[s] sizes[p] third[s] thirds[p] vacuole[s] vacuoles[p] valve[s] valves[p] width[s] widths[p]"
	my $pn = ""; #previous n
	foreach my $n (@nouns){#convert to hash
		if($n =~ /\w/ and $n !~ /\b(NUM|$NUMBERS|$CLUSTERSTRINGS|$CHARACTER|$PROPERNOUNS)\b/){
	    	#note: what if the same word has two different pos?
			my @ns = split(/\|/,$n);
			foreach my $w (@ns){
				if($w =~ /(\w+)\[([spn])\]/){
					  #my $sth = $dbh->prepare("insert into ".$dataprefix."_wordpos values(\"$w\",\"$2\",\"\",1,1)");
		              #            $sth->execute();
	    		      update($1, $2, "*", "wordpos", 0);
	    		      #populate singularpluralpair table with Ns
	    		      if($pn =~ /\[s\]$/ && $w =~ /\[p\]$/){
	    		      		my $sg = $pn;
	    		      		my $pl = $w;
	    		      		$pn =~ s#\w\w\w\[s\]$##g;
	    		      		$w =~ s#\w\w\w\[p\]$##g;
	    		      		if($pn =~ /^$w/ || $w=~/^$pn/){
	    		      			$sg =~ s#\[s\]$##g;
	    		      			$pl =~ s#\[p\]$##g;
	    		      			addsingularpluralpair($sg, $pl);
	    		      			$pn = "";
	    		      		}
	    		      }else{
	    		      	$pn = $w;
	    		      }
				}
			}
		}
	}
}


#suffix: -fid(adj), -form (adj), -ish(adj),  -less(adj), -like (adj)),  -merous(adj), -most(adj), -shaped(adj), -ous(adj)
#        -ly (adv), -er (advj), -est (advj), 
#foreach unknownword in unknownwords table
#   seperate root and suffix
#   if root is a word in WN or unknownwords table
#   make the unknowword a "b" 
sub  posbysuffix{
	my($sth, $pattern, $unknownword);
	$pattern = "^[a-z_]+(".$SUFFIX.")\$";
	$sth = $dbh->prepare("select word from ".$dataprefix."_unknownwords where word rlike '$pattern' and flag= 'unknown'");
	$sth->execute() or warn "$sth->errstr\n";
	while(($unknownword) = $sth->fetchrow_array()){
		if($unknownword =~/^(.*?)($SUFFIX)$/){
			if(containsuffix($unknownword, $1, $2)){
				update($unknownword, "b", "*", "wordpos", 0);
				print "posbysuffix set $unknownword a boundary word\n" if $debug;
			}			
		}
	}
	
	$pattern = "^[._.][a-z]+"; #, _nerved
	$sth = $dbh->prepare("select word from ".$dataprefix."_unknownwords where word rlike '$pattern' and flag= 'unknown'");
	$sth->execute() or warn "$sth->errstr\n";
	while(($unknownword) = $sth->fetchrow_array()){
		update($unknownword, "b", "*", "wordpos", 0);
		print "posbysuffix set $unknownword a boundary word\n" if $debug;
	}
}

#return 0 or 1 depending on if the word contains the suffix as the suffix
sub containsuffix{
	my($word, $base, $suffix) = @_;
	my($flag, $wnoutputword, $wnoutputbase, $wordinwn, $baseinwn, $sth);
	$flag = 0;
	
	$base =~ s#_##g; #cup_shaped
	$wnoutputword = `wn $word -over`;
  	if ($wnoutputword !~/\w/){#word not in WN
		$wordinwn = 0;
  	}else{ 	#found $word in WN:
  		$wnoutputword =~ s#\n# #g;
  		$wordinwn = 1;
  	}
  	
  	$wnoutputbase = `wn $base -over`;
  	if ($wnoutputbase !~/\w/){#word not in WN
		$baseinwn = 0;
  	}else{ 	#found $word in WN:
  		$wnoutputbase =~ s#\n# #g;
  		$baseinwn = 1;
  	}
	
	if($suffix eq "ly"){#if WN pos is adv, return 1: e.g. ly, or if $base is in unknownwords table
		if($wordinwn){
			if($wnoutputword =~/Overview of adv $word/){
				return 1;;
			}
		}
		$sth = $dbh->prepare("select word from ".$dataprefix."_unknownwords where word = '$base'");
		$sth->execute() or warn "$sth->errstr\n";
		return 1 if $sth->rows > 0;
	}elsif($suffix eq "er" || $suffix eq "est"){#if WN recognize superlative, comparative adjs, return 1: e.g. er, est
		if($wordinwn){
			if($wnoutputword =~/Overview of adj (\w+)/){#$word = softer, $1 = soft vs. $word=$1=neuter
				return 1 if $word=~/^$1\w+/; 
			}
		}
	}else{#if $base is in WN or unknownwords table, or if $word has sole pos adj in WN, return 1: e.g. scalelike 
		if($baseinwn){return 1;}
		if($wnoutputword =~/Overview of adj/ && $wnoutputword !~/Overview of .*? Overview of/){
			return 1;;			
		}
		$sth = $dbh->prepare("select word from ".$dataprefix."_unknownwords where word = '$base'");
		$sth->execute() or warn "$sth->errstr\n";
		return 1 if $sth->rows > 0;
	}
	return $flag;
}

sub resetcounts{
	my ($sth);
	$sth = $dbh->prepare("update ".$dataprefix."_wordpos set certaintyu=0, certaintyl=0");
	$sth->execute() or warn "$sth->errstr\n";
}

sub markknown{
	my ($word, $pos,$role, $table, $increment) = @_;
	my ($sth, $pattern, $newword, $sign, $otherprefix, $spwords);
	#if($word !~ /\w/) {
	#	print "$word is not a word, not updated\n" if $debug;
	#	return $sign;
	#}
	if($word =~/^($FORBIDDEN)$/){return $sign;}
	$sign += processnewword($word, $pos, $role, $table, $word, $increment);
	if($word =~ /^($stop)$/) {
		$sign += processnewword($word, $pos, $role, $table, $word, $increment);
		return $sign;
	} 
	#remove/replace prefix from $word
	if($word =~/^($PREFIX)(\S+)/){
		my $tmp = $2;
		$otherprefix = $PREFIX;
		$otherprefix =~s#\b$1\b##;
		$otherprefix =~ s#\|\|#|#;
		$otherprefix =~ s#^\|##;
		$spwords = "(".escape(singularpluralvariations($tmp)).")";
		$pattern = "^(".$otherprefix.")?".$spwords."\$";
		$sth = $dbh->prepare("select word from ".$dataprefix."_unknownwords where word rlike '$pattern' and flag= 'unknown'");
		$sth->execute() or warn "$sth->errstr\n";
		while(($newword) = $sth->fetchrow_array()){
			$sign += processnewword($newword, $pos, "*", $table, $word, 0); #6/11/09 $role => *, add 0
			print "by removing prefix of $word, know $newword is a [$pos] \n" if $debug;
		}
	}#else{ #3/21/09 psuedo/inter/area
		#add prefix to $word
		$spwords = "(".escape(singularpluralvariations($word)).")";
		$pattern = "^(".$PREFIX.")".$spwords."\$"; #$word=shrubs; $pattern = (pre|sub)shrubs
		#print "$pattern\n";
		$sth = $dbh->prepare("select word from ".$dataprefix."_unknownwords where word rlike '$pattern' and flag = 'unknown'");
		$sth->execute() or warn "$sth->errstr\n";
		while(($newword) = $sth->fetchrow_array()){
			$sign += processnewword($newword, $pos,"*", $table, $word, 0);
			print "by adding a prefix to $word, know $newword is a [$pos] \n" if $debug;
		}
		#word_$spwords
		$spwords = "(".escape(singularpluralvariations($word)).")";
		$pattern = ".*_".$spwords."\$";
		$sth = $dbh->prepare("select word from ".$dataprefix."_unknownwords where word rlike '$pattern' and flag = 'unknown'");
		$sth->execute() or warn "$sth->errstr\n";
		while(($newword) = $sth->fetchrow_array()){
			$sign += processnewword($newword, $pos,"*", $table, $word, 0);
			print "by adding a prefix word to $word, know $newword is a [$pos] \n" if $debug;
		}
	#}
	return $sign;
}

#return singular and plural variations of word $word:
sub singularpluralvariations{
	my $word = shift;
	my ($sth, $variations, $pl, $sg);
	$variations = "$word|";
	$sth = $dbh->prepare("select plural from ".$dataprefix."_singularplural where singular = '$word'");
	$sth->execute() or warn "$sth->errstr\n";	
	while(($pl) = $sth->fetchrow_array){
		$variations .= $pl."|";
	}
	
	$sth = $dbh->prepare("select singular from ".$dataprefix."_singularplural where plural = '$word'");
	$sth->execute() or warn "$sth->errstr\n";	
	while(($sg) = $sth->fetchrow_array){
		$variations .= $sg."|";
	}
	$variations =~ s#\|+$##g;
	return $variations;
}




sub processnewword{
	my ($newword, $pos, $role, $table, $sourceword, $increment) = @_;
	my $sign = 0;
	#remove $newword from ".$dataprefix."_unknownwords
	updateunknownwords($newword, $sourceword);
	#insert $newword to $table
	$sign += updatePOS($newword, $pos, $role, $increment) if $table eq "wordpos";
	$sign += addmodifier($newword, $increment) if $table eq "modifiers";
	return $sign;
}

sub updateunknownwords{
	my ($newword, $flag) = @_;
	my $sth1 = $dbh->prepare("update ".$dataprefix."_unknownwords set flag = '$flag' where word = '$newword'");
	$sth1->execute() or warn "$sth1->errstr\n";
	
}

############################################################################################
########################                                   #################################
########################  deal with words that plays N, and B roles              ###########
############################################################################################
my $NONS = ""; #4/20/09
sub resolvenmb{
	my ($sth, $word, $sth1, $sentid, $sentence);
	$sth = $dbh->prepare("select word from ".$dataprefix."_wordpos where (word in (select word from ".$dataprefix."_wordpos where pos ='s') or word in (select distinct tag from ".$dataprefix."_sentence) )and word in (select word from ".$dataprefix."_wordpos where pos ='b')");
	$sth->execute() or warn "$sth->errstr\n";
	while(($word) = $sth->fetchrow_array()){
		$sth1 = $dbh->prepare("select * from ".$dataprefix."_modifiers where word = '$word'");
		$sth1->execute() or warn "$sth1->errstr\n";
		if($sth1->rows() > 0){
			#remove the N role
			$sth1 = $dbh->prepare("delete from ".$dataprefix."_wordpos where word = '$word' and pos ='s'");
			$sth1->execute() or warn "$sth1->errstr\n";
			#reset tags
			$sth1 = $dbh->prepare("update ".$dataprefix."_sentence set modifier='', tag =NULL where tag ='$word' or tag like '% $word'");
			$sth1->execute() or warn "$sth1->errstr\n";
			$NONS .="$word|";
			print "$word roles reduced to M/B, 's' role removed and clauses set to NULL tag, sentences retagged\n" if $debug;
		}
	}
	#4/20/09
	chop($NONS);
	#retag clauses with <N><M><B> tags
	$sth = $dbh->prepare("select sentid, sentence from ".$dataprefix."_sentence where sentence like '%<N><M><B>%'");
	$sth->execute() or warn "$sth->errstr\n";
	
	while(($sentid, $sentence) = $sth->fetchrow_array()){
		$sentence =~ s#<[ON]><M><B>#<M><B>#g;
		$sentence =~ s#</B></M></[ON]>#</B></M>#g;
		$sth1 = $dbh->prepare("update ".$dataprefix."_sentence set sentence = '$sentence' where sentid =$sentid");
		$sth1->execute() or warn "$sth1->errstr\n";
	}
	
}
############################################################################################
########################                                   #################################
########################  correct markups that used an adj as an s, e.g lateral  ###########
############################################################################################
#correct markups that used an adj as an s, e.g lateral
#adult, juvenile
sub adjsverification{
	my ($sth, $sth1, $sentid, $sentence, $tag, $modifier, $ptn, $word);
	print "\n========Correct adj-singular noun markup\n" if $debug;
	#n [bm]+ n where [bm] not proposition
	$ptn = "^<N>([a-z]+)</N> ([^N,;.]+ <N>[a-z]+</N>)";
	$sth = $dbh->prepare("select sentid, sentence from ".$dataprefix."_sentence where sentence COLLATE utf8_bin rlike '$ptn'");
	$sth->execute() or warn "$sth->errstr\n";
	while(($sentid, $sentence, $tag, $modifier)=$sth->fetchrow_array()){
		if($sentence =~ /$ptn/){
			my $wrong = $1 if isseentag($2) and getnumber($1) ne "p";
			#$1 is not noun
			if($wrong =~ /\w/){
				#remove $wrong from pos
				#update $wrong as an m
				#reset sentence tagged with $wrong NULL
				n2m($wrong);
				#update other words tied to $wrong in unknownwords
				$sth1 = $dbh->prepare("select word from ".$dataprefix."_unknownwords where flag = '$wrong'");
				$sth1->execute() or warn "$sth1->errstr\n";
				while(($word) = $sth1->fetchrow_array()){
					n2m($word);
				}
			}
		}
	}
}
#4/7/09
sub isseentag{
	my $raw = shift;
	$raw =~ s#<\S+?>##g;
	my $sth = $dbh->prepare("select * from ".$dataprefix."_sentence where tag like '$raw%'");
	$sth->execute() or warn "$sth->errstr\n";
	if($sth->rows()>0){
		return 1;
	}
	return 0;
}
#4/7/09
sub n2m{
	my $wrong = shift;
	#remove $wrong from pos
	my $sth1 = $dbh->prepare("delete from ".$dataprefix."_wordpos where word = '$wrong' and pos in ('s','p','n')");
	$sth1->execute() or warn "$sth1->errstr\n";
	#update $wrong as an m
	update($wrong, "m", "", "modifiers", 1);
	#reset tag to NULL
	$sth1 = $dbh->prepare("update ".$dataprefix."_sentence set tag = NULL where tag = '$wrong' or tag like '% $wrong'");
	$sth1->execute() or warn "$sth1->errstr\n";
	
}

############################################################################################
########################                                   #################################
########################set andor tags                     #################################
############################################################################################

#called on tagged sentences
sub setandor{
	my ($sth1, $sth, $sentid, $sentence, $lead, $ptn1, $ptn2);
	print "\n========Tag and/or sentences andor\n" if $debug;
	
	#$ptn1="^(?:[mbq]{0,3}[onp](?:,|(?=&)))+&(?:[mbq]{0,3}[onp])"; #n,n,n&n
	#$ptn2="^(?:[mbq]{0,3}(?:,|(?=&)))+&(?:[mbq]{0,3})[onp]"; #m,m,&mn
	
	#3/28/09: relax the condition a little #4/26/09 add n in [mbq] check sentid 5481 for effects
	$ptn1="^(?:[mbq,]{0,10}[onp]+(?:,|(?=&)))+&(?:[mbq,]{0,10}[onp]+)"; #n,n,n&n
	$ptn2="^(?:[mbq,]{0,10}(?:,|(?=&)))+&(?:[mbq,]{0,10})[onp]+"; #m,m,&mn
	#$ptn1="^(?:[mbq,]{0,10}[onp](?:,|(?=&)))+&(?:[mbq,]{0,10}[onp])"; #n,n,n&n
	#$ptn2="^(?:[mbq,]{0,10}(?:,|(?=&)))+&(?:[mbq,]{0,10})[onp]"; #m,m,&mn
	
	#step through all sentences except ones that marked 'ignore'
	#$sth1 = $dbh->prepare("select sentid, sentence from ".$dataprefix."_sentence where tag !='ignore' or isnull(tag) ");
	$sth1 = $dbh->prepare("select sentid, sentence from ".$dataprefix."_sentence"); #5/01/09 check 653, 6985, 6699
	$sth1->execute();
	while(($sentid, $sentence, $lead)=$sth1->fetchrow_array()){
		if(isandorsentence($sentid, $sentence, $lead, $ptn1, $ptn2)){
			$sth = $dbh->prepare("update ".$dataprefix."_sentence set tag = 'andor' where sentid = $sentid ");
			$sth->execute();
		}
	}
}

sub isandorsentence{
	my($sentid, $sentence, $lead, $ptn1, $ptn2) = @_;
	my ($sentptn,$token, $limit, @words);
	#return 1 if $lead =~/\b(and|or|\/)\b/;
	#$sentence =~ s#<\S+?>##g;
	#$andorptn = "(?:and|or|\/)";
	#$wptn = "(?:(?:[\\w_]+\\s){1,3}(?:,|(?=$andorptn))\\s*)"; #annuals  ,  biennials  ,  perennials  ,  subshrubs  ,  shrubs  ,  vines  ,  or  trees  . 
	#$ptn = "$wptn+\\b$andorptn\\b";
	
	$token = "(and|or|nor|\/|and \/ or)";
	$limit = 80;
	@words = split(/\s+/, $sentence);
	$sentptn = sentenceptn($token, $limit, @words);
	$sentptn =~ s#t#m#g; #ignore the distinction between type modifiers and modifiers
	 
	#$ptn1="^(?:[mbq]{0,3}[onp](?:,|(?=&)))+&(?:[mbq]{0,3}[onp])"; #n,n,n&n
	#$ptn2="^(?:[mbq]{0,3}(?:,|(?=&)))+&(?:[mbq]{0,3})[onp]"; #m,m,&mn
	#print "in isandorsentence: [$sentptn] $sentid: $sentence\n" if $debug;
	if($sentptn=~/($ptn1)/ || $sentptn =~/($ptn2)/){
		my $end = $+[1];
		my $matchedwords = join(" ", splice(@words, 0, $end));
		if($matchedwords =~/\b($PROPOSITION)\b/){
			return 0;
		}
		print "in isandorsentence: yes [$sentptn] $sentid:$sentence\n" if $debug;
		return 1;
	}
	print "in isandorsentence: no [$sentptn] $sentid:$sentence\n" if $debug;
	return 0;
}

#to null
sub resetandor{
	my ($sth);
	$sth = $dbh->prepare("update ".$dataprefix."_sentence set tag = NULL where tag = 'andor' ");
	$sth->execute();
}

#Find Modifier/Organ for the same Ox: M1 Ox, M2 Ox Example: inner phyllaries, middle phyllaries
#Find Mx/Oy where Ox != Oy Example: inner florets
# ==>inner/middle = type modifier
# Find TM C (character) patterns => TM = adjective nouns
# outer and middle => outer is adject noun
# outer and mid => mid is adject noun
#===> infer more boundary words/structure: outer [ligules], inner [fertile]

sub adjectivesubjectbootstrapping{
	print "start adjectivesubjectbootstrapping\n" if $debug;

	my $flag;
	my $count = 0;
	do{
	   print "============================adjective subject round ".$count++."\n" if $debug;
	   $flag = 0;
	   print "========tag all sentences\n" if $debug;
	   tagallsentences("singletag", "sentence");
	   print "========starts adjective subject markup\n" if $debug;
	   $flag += adjectivesubjects(); #may discover new modifier, new boundary, and new nouns.
	   print "========starts to discover new modifiers\n" if $debug;
	   $flag += discovernewmodifiers();
	   print "========and/or\n" if $debug;
	   $flag += andor(); #work on tag='andor' clauses 3/12/09, move to the main bootstrapping
	   untagsentences();
	   print "========starts doit markup\n" if $debug;
	   $flag += doitmarkup(); 
	}while ($flag>0);
	#reset andor tags to null
	print "========reset unsolvable andor to NULL\n" if $debug;
	my $sth = $dbh->prepare("update ".$dataprefix."_sentence set tag = NULL where tag = 'andor' ");
	$sth->execute() or warn "$sth->errstr\n";
	print "========last round of adjectivesubjects\n" if $debug;
	#cases releazed from andor[m&mn] may be marked by adjectivesubjects
	tagallsentences("singletag", "sentence");
	adjectivesubjects();
	print "end adjectivesubjectbootstrapping\n" if $debug;
		   
}


############################################################################################
########################adjectivesubjects                  #################################
############################################################################################

#works on annotated sentences that starts with a M
#in all non-ignored sentences, find sentences that starts with a modifer <m> followed by a boundary word <b>
# (note, if the <B> is a punct mark, this sentence should be tagged as ditto)
#use the context to find the tag, use the modifier as the modifie (markup process, no new discovery)


#for "modifier unknown" pattern, check WNPOS of the "unknown" to decide if "unknown" 
#is a structure name (if it is a pl) or a boundary word (may have new discoveries)
#works on sentences, not leads

sub adjectivesubjects{
	my $flag = 0;
	my ($sth,$sth1, $typemodifiers, $sentence, $temp, $sentid, $word, $pos, $count, $start, $modifier, $tag, $newm);
	#collect evidence for the usage of "modifier boundry": 
	$sth = $dbh->prepare("select sentence from ".$dataprefix."_sentence where sentence regexp '<M>[^[:space:]]+</M> <B>[^,\\.].*' and (tag != 'ignore' or isnull(tag))");
	$sth->execute() or warn "$sth->errstr\n";
	while(($sentence) = $sth->fetchrow_array()){
		while($sentence =~ /.*?<M>(\S+)<\/M> <B>[^,\.]+<\/B> (.*)/){ #filter and collect type modifiers
			$sentence = $2;
			$temp = $1;
			$temp =~ s#<\S+?>##g;
			if($typemodifiers !~ /\b$temp\b/i){
				$typemodifiers .= $temp."|"; 
				$sth1 = $dbh->prepare("update ".$dataprefix."_modifiers set istypemodifier = 1 where word = '$temp'");
				$sth1->execute() or warn "$sth1->errstr\n";
			}			
		}
	}#end while
	chop($typemodifiers);
	print "type modifiers: $typemodifiers\n" if $debug;
	#process "typemodifier unknown" patterns: 
	$sth = $dbh->prepare("select sentid, sentence from ".$dataprefix."_sentence where (isnull(tag) or tag ='' or tag='unknown') and sentence regexp '<M>[^[:space:]]*($typemodifiers)[^[:space:]]*</M> .*'"); #<m><b>basal<\b><\m>
	$sth->execute() or warn "$sth->errstr\n";
	while(($sentid, $sentence) = $sth->fetchrow_array()){
		my $count = 0;
		print "\n\n adjective subjects mark up: $sentid: $sentence\n\n" if $debug;
		#functionally <m>basal</m> and / or <m>cauline</m> flat
		#<m>a</m> or <m><b>basal</b></m> and / or <m>cauline</m> <n>leaves</n>
		#basal and <m>cauline</m> <b>flat</b>
		#<m>basal</m> and cauline flat
		#<m>basal</m> (0-) 5
		while($sentence =~ /(.*?)((?:(\S+)\s*(?:and|or|nor|and \/ or|or \/ and)\s*)*(?:<M>\S+<\/M>\s*)+) (\S+)\s*(.*)/){ #find unknown words
			my $knownpos = 0;
			$start = $1;
			$modifier = $2;
			$newm = $3;
			$sentence = $5;
			$word = $4;
			if($word =~ /\b($FORBIDDEN)\b/){$count++; next;}
			if($newm =~/<N>/i ||$start =~/<N>/i){$count++; next;}
			#M[MB][,in] =>MB[,in]
			#[MB][MB][,]
			#if($count==0 && ((($word =~ /[;,:]/ || $word=~/\b(?:$PROPOSITION)\b/ )&& $sentence =~ /^\s*<N>/) || ($word =~ /[\.:;,]/ && $sentence !~ /\w/))){
			if($count==0 && ((($word =~ /[;,]/ || $word=~/\b(?:$PROPOSITION)\b/)) || ($word =~ /[\.;,]/ && $sentence !~ /\w/))){
				#if($sentence !~ /^(\S+)?\s*<N>/ && $word!~/with/ && ($modifier =~/^(<M>)?<B>(<M>)?\w+(<\/M)?<\/B>(<\/M>)? (?:and|or|and \/ or|or \/ and)?\s*(<[BM]>)+\w+(<\/[BM]>)+\s*$/ || $modifier =~/^(<[BM]>)+\w+(<\/[BM]>)+$/)){ #start with a <[BM]>, followed by a <[BM]>
				if($word!~/\b(with|without|of)\b/ && ($modifier =~/^(<M>)?<B>(<M>)?\w+(<\/M)?<\/B>(<\/M>)? (?:and|or|nor|and \/ or|or \/ and)?\s*(<[BM]>)+\w+(<\/[BM]>)+\s*$/ || $modifier =~/^(<[BM]>)+\w+(<\/[BM]>)+$/)){ #start with a <[BM]>, followed by a <[BM]>
					#<M><B>basal</B></M> and <M><B>cauline</B></M> <B>;</B>
					#<B>usually</B> <M><B>taprooted</B></M> <B>.</B>
					tagsentwmt($sentid, $sentence, "", "ditto", "adjectivesubject[ditto]");
					$count++;
					next;
				}elsif($modifier =~ /^(.*) (\S+)$/){
					#modifier={<M>outer</M> <M><B>pistillate</B></M>} word= <B>,</B> sentence= <N>corollas</N>....
					#make the last modifier b
					$modifier = $1;
					my $b = $2;
					my $bcopy = $b;
					$b =~ s#<\S+?>##g; #remove tag from new <b>
					update($b, "b", "", "wordpos", 1) if $bcopy !~ /<B>/;
					$tag = getparentsentencetag($sentid);
					my ($m,$tag) = getpartsfromparenttag($tag); #the last word in $tag is $tag, others $m
					$modifier =~ s#<\S+?>##g;
					$modifier .= " ".$m if $m =~/\w/;
					tagsentwmt($sentid, $sentence, $modifier, $tag, "adjectivesubject[M-B,]");
					#$sth1 = $dbh->prepare("update ".$dataprefix."_sentence set modifier = '$modifier', tag = '$tag' where sentid = $sentid");
					#$sth1->execute() or warn "$sth1->errstr\n";
					#print "use [$modifier/$tag] to mark up sentence: ".$sentid."\n" if $debug; 
					$count++;
					next;
				}
			}
			#get new modifier from modifiers like  "mid and/or <m>distal</m>"
			if($newm !~ /</ && $newm=~/\w/ && $start =~ /,(?:<\/B>)?\s*$/){
				$flag += update($newm, "m", "","modifiers", 1);
				print "find a modifier [E0]: $newm\n" if $debug; 
			}
			if($word =~ /([A-Z])>(<([A-Z])>)?(.*?)</){ #pos = "N"/"B"
				my $t1= $1; 
				my $t2 = $3;
				$word = $4;
				$pos = $t1.$t2;
				if(length($pos) > 1){#if <N><B>, decide on one tag
					if(($sentence =~ /^\s*<B>[,;:]<\/B>\s*<N>/) || ($sentence =~ /^\s*<B>\.<\/B>\s*$/)){
						$pos = "B";
					}else{
						$pos = "N";
					}
				}	
				$knownpos = 1;
			}else{
				$pos = checkpos($word, "one");
			}
			$pos = $pos eq "?"? getnumber($word): $pos;#narrow =>s, CheckWN is not good.
			if($pos eq "p" || $pos eq "N"){#markup sentid, update pos for $word, new modifier
				$flag += update($word, "p", "-", 1) if (!$knownpos); #if "n", not renewly learned, no update.
				print "update [$word] pos: p\n" if (!$knownpos) && $debug;
			 	if($count==0 && ($start =~ /^\S+\s?(?:and |or |and \/ or |or \/ and )?$/ || length($start) == 0)){ #tag and update pos if this pattern appear at the begining of a sentence
					$modifier = $start.$modifier; #<m>cauline</m> and <m>basal</m> leaves; functionally <m>staminate</m> florets
					$modifier =~ s#<\S+?>##g;
					$word =~ s#<\S+?>##g;
					tagsentwmt($sentid, $sentence, $modifier, $word, "adjectivesubject[M-N]");
					#$sth1 = $dbh->prepare("update ".$dataprefix."_sentence set modifier = '$modifier', tag = '$word' where sentid = $sentid");
					#$sth1->execute() or warn "$sth1->errstr\n";
					#print "use [$modifier/$word] to mark up sentence: ".$sentid."\n" if $debug; #TODO: increase count for modidifier
					#new modifier
					$start =~ s#\s*(and |or |and \/ or |or \/ and )\s*##g; 
					$start =~s#<\S+?>##g;
					while($start =~ /^($stop)\b/){$start =~ s#^($stop)\b\s*##g;}
					if(length($start) > 0){
						$flag += update($start, "m", "", "modifiers", 1);
						print "find a modifier [E]: $start\n" if $debug; 
					}
			 	}
			}else{ #not p
				$flag += update($word, "b", "", "wordpos", 1) if (!$knownpos);#update pos for $word, markup sentid (get tag from context), new modifier
				print "update [$word] pos: b\n" if $debug;
				if($count==0 && ($start =~/^\S+\s?(?:and |or |and \/ or |or \/ and )?$/ || length($start) == 0)){ #tag and update pos if this pattern appear at the begining of a sentence
					while($start =~ /^($stop|$FORBIDDEN|\w+ly)\b/){$start =~ s#^($stop|$FORBIDDEN|\w+ly)\b\s*##g;}
					$modifier = $start.$modifier; #<m>cauline</m> and <m>basal</m> leaves; functionally <m>staminate</m> florets
					$modifier =~ s#<\S+?>##g;
					$tag = getparentsentencetag($sentid);
					my ($m,$tag) = getpartsfromparenttag($tag);
					$modifier .= " ".$m if $m =~/\w/;
					tagsentwmt($sentid, $sentence, $modifier, $tag, "adjectivesubject[M-B]");
					#$sth1 = $dbh->prepare("update ".$dataprefix."_sentence set modifier = '$modifier', tag = '$tag' where sentid = $sentid");
					#$sth1->execute() or warn "$sth1->errstr\n";
					#print "use [$modifier/$tag] to mark up sentence: ".$sentid."\n" if $debug;
					#new modifier
					$start =~ s#\s*(and |or |and \/ or |or \/ and )\s*##g; 
					$start =~s#<\S+?>##g;
					if(length($start) > 0){
						$flag += update($start, "m", "", "modifiers", 1) if $start !~ /ly\s*$/ and  $start !~/\b($stop|$FORBIDDEN)\b/;
						print "find a modifier [F]: $start\n" if $debug; 
					}
			 	}
			}
			$count++;
		}#end while
	}#end while
return $flag;	
}

#discover new modifiers using and/or pattern
#for "modifier and/or unknown boundary" pattern or "unknown and/or modifier boundary" pattern, make "unknown" a modifier
sub discovernewmodifiers{	#each modifier is one word
	my ($sth, $sentid, $sentence, $sth1, $sign, $pos);
	#"modifier and/or unknown boundary" pattern 
	$sth = $dbh->prepare("select sentid, sentence from ".$dataprefix."_sentence where (tag != 'ignore' or isnull(tag)) and sentence regexp '<M>[^[:space:]]+</M> (or|and|and / or|or / and) .*'");
	$sth->execute() or warn "$sth->errstr\n";
	while(($sentid, $sentence) = $sth->fetchrow_array()){
		$pos="";
		#if "<m>xxx</m> (and|or) yyy (<b>|\d)" pattern appear at the beginning or is right after the 1st word of the sentence, mark up the sentence, add yyy as a modifier
		if($sentence =~/^(?:\w+\s)?<M>(\S+)<\/M> (and|or|nor|and \/ or|or \/ and) ((?:<[^M]>)*[^<]+(?:<\/[^M]>)*) <B>[^,;:\.]/){
			my $modifier =$1." ".$2." ".$3;
			my $newm = $3;
			if($newm !~/\b($stop)\b/){ 
				$modifier =~ s#<\S+?>##g;
				if($newm =~ /(.*?>)(\w+)<\//){ #N or B
					$newm = $2;
					$pos = $1;
				}
				if($pos =~ /<N>/){ #update N to M: retag sentences tagged as $newm, remove [s] record from ".$dataprefix."_wordpos 
					$sign += changePOS($newm, "s", "m", "", 1);
				}else{#B
					$sign +=  update($newm, "m", "", "modifiers", 1);
				}
				print "find a modifier [A]: $newm\n" if $debug;
				my $tag = getparentsentencetag($sentid);
				my ($m,$tag) = getpartsfromparenttag($tag);
				$modifier .= " ".$m if $m =~/\w/;
				tagsentwmt($sentid, $sentence, $modifier, $tag, "discovernewmodifiers");
				#$sth1 = $dbh->prepare("update ".$dataprefix."_sentence set modifier = '$modifier', tag = '$tag' where sentid = $sentid");
				#$sth1->execute() or warn "$sth1->errstr\n";
				#print "use [$modifier/$tag] to mark up sentence: ".$sentence."\n" if $debug;
			}
		}elsif($sentence =~/<M>(\S+)<\/M> (and|or|nor|and \/ or|or \/ and) (\w+) <B>[^,;:\.]/){
			#if the pattern appear in the middle of the sentence, add yyy as modifier
			my $newm = $3;
			$sign +=  update($newm, "m", "", "modifiers", 1);
			print "find a modifier[B]: $newm\n" if $debug;
		} 		
	}
	#"unknown and/or modifier boundary"
	$sth = $dbh->prepare("select sentid, sentence from ".$dataprefix."_sentence where (tag != 'ignore' or isnull(tag)) and sentence regexp '[^[:space:]]+ (and|or|nor|and / or|or / and) <M>[^[:space:]]+</M> .*'");
	$sth->execute() or warn "$sth->errstr\n";
	while(($sentid, $sentence) = $sth->fetchrow_array()){
		$pos = "";
		#if "xxx (and|or|nor) <m>yyy</m> (<b>|\d)" pattern appear at the beginning or is right after the 1st word of the sentence, mark up the sentence, add yyy as a modifier
		if($sentence =~/^(?:\w+\s)?((?:<[^M]>)*[^<]+(?:<\/[^M]>)*) (and|or|nor|and \/ or|or \/ and) <M>(\S+)<\/M> <B>[^:;,\.]/){
			my $modifier = $1." ".$2." ".$3;
			my $newm = $1;
			$modifier =~ s#<\S+?>##g;
			if($newm =~ /(.*?>)(\w+)<\//){ #N or B
				$newm = $2;
				$pos = $1
			}
			if($pos =~ /<N>/){ #update N to M 
				$sign += changePOS($newm, "s", "m", "", 1); #update $newm to m
			}else{#B
				$sign +=  update($newm, "m", "", "modifiers", 1);
			}
			print "find a modifier [C]: $newm\n" if $debug;
			my $tag = getparentsentencetag($sentid);
			my ($m,$tag) = getpartsfromparenttag($tag);
			$modifier .= " ".$m if $m =~/\w/;
			tagsentwmt($sentid, $sentence, $modifier, $tag, "discovernewmodifiers");
			#$sth1 = $dbh->prepare("update ".$dataprefix."_sentence set modifier = '$modifier', tag = '$tag' where sentid = $sentid");
			#$sth1->execute() or warn "$sth1->errstr\n";
			#print "use [$modifier/$tag] to mark up sentence: ".$sentence."\n" if $debug;
		}elsif($sentence =~/(\w+) (and|or|nor|and \/ or|or \/ and) <M>(\S+)<\/M> <B>[^,:;\.]/){
			#if the pattern appear in the middle of the sentence, add yyy as modifier
			my $newm = $1;
			$sign +=  update($newm, "m", "", "modifiers", 1);
			print "find a modifier [D]: $newm\n" if $debug;
		} 		
	}
	return $sign;
}

#correct the pos of the word from N to M
sub changePOS{
#sub n2m{
	my ($word, $oldpos, $newpos, $role, $increment) = @_;
	my ($sth, $sentid, $modifier, $tag, $sentid, $sentence, $sth1, $sign);
	$oldpos = lc $oldpos;
	$newpos = lc $newpos; 

	print "change pos of [$word] from $oldpos to $newpos, with increment $increment\n" if $debug;
	if($oldpos =~ /s/ && $newpos=~/m/){ #s2m, 
		#remove its "s" pos from ".$dataprefix."_wordpos table
		#$sth = $dbh->prepare("delete from ".$dataprefix."_wordpos where word = '$word' and pos = '$oldpos'");
		#$sth->execute() or warn "$sth->errstr\n";
		discount($word, $oldpos, $newpos, "all");
		$sign +=  markknown($word, "m", "", "modifiers", $increment);
		
		#all sentences tagged with $word (m), retag by finding their parent tag. #4/7/09 tocheck
		$sth = $dbh->prepare("select sentid, modifier, tag, sentence from ".$dataprefix."_sentence where tag = '$word'");
		$sth->execute() or warn "$sth->errstr\n";
		while(($sentid, $modifier, $tag, $sentence) = $sth->fetchrow_array()){
			$tag = getparentsentencetag($sentid);
			$modifier = $modifier." ".$word;
			$modifier =~ s#^\s*##g;
			my ($m,$tag) = getpartsfromparenttag($tag);
			$modifier .= " ".$m if $m =~/\w/;
			tagsentwmt($sentid, $sentence, $modifier, $tag, "changePOS[n->m:parenttag]");
		}
	}elsif($oldpos =~ /s/ && $newpos=~/b/){#s2b
		#update pos table
		$sth = $dbh->prepare("select certaintyu from ".$dataprefix."_wordpos where word='$word' and pos='$oldpos' ");
    	$sth->execute();
    	my ($certaintyu) = $sth->fetchrow_array();
    	#$certaintyu++; #6/11/09
    	$certaintyu += $increment;
    	discount($word, $oldpos, $newpos, "all");
    	$sth = $dbh->prepare("insert into ".$dataprefix."_wordpos values ('$word', '$newpos', '$role', $certaintyu, 0)");
    	$sth->execute() or warn "$sth->errstr\n";;
		print "\t: change [$word($oldpos => $newpos)] role=>$role\n" if $debug;
		$sign++;		
		#all sentences tagged with $word (b), retag.
		$sth = $dbh->prepare("select sentid, modifier, tag, sentence from ".$dataprefix."_sentence where tag = '$word'");
		$sth->execute() or warn "$sth->errstr\n";
		while(($sentid, $modifier, $tag, $sentence) = $sth->fetchrow_array()){
			tagsentwmt($sentid, $sentence, "", "NULL", "changePOS[s->b: reset to NULL]");
		}
	}elsif($oldpos =~ /b/ && $newpos=~/s/){#b2s
		#update pos table
		$sth = $dbh->prepare("select certaintyu from ".$dataprefix."_wordpos where word='$word' and pos='$oldpos' ");
    	$sth->execute();
    	my ($certaintyu) = $sth->fetchrow_array();
    	#$certaintyu++; #6/11/09
    	$certaintyu += $increment;
    	discount($word, $oldpos, $newpos, "all");
    	$sth = $dbh->prepare("insert into ".$dataprefix."_wordpos values ('$word', '$newpos', '$role', $certaintyu, 0)");
    	$sth->execute() or warn "$sth->errstr\n";
		print "\t: change [$word($oldpos => $newpos)] role=>$role\n" if $debug;
		$sign++;		
	}
	
	#update certaintyl = sum (certaintyu)
	$sth = $dbh->prepare("select sum(certaintyu) from ".$dataprefix."_wordpos where word=\"$word\"");
    $sth->execute();
	my ($certaintyl) = $sth->fetchrow_array();
	if(defined $certaintyl && $certaintyl >0){
		$sth = $dbh->prepare("update ".$dataprefix."_wordpos set certaintyl=$certaintyl where word=\"$word\"");
    	$sth->execute();
		print "\t: total occurance of [$word] =$certaintyl\n" if $debug;
	}
	return $sign;
}




#find the tag of the sentence of which this sentid (clause) is a part of
sub getparentsentencetag{
	my $sentid = shift;
	my ($sth, $modifier, $tag, $thissent);
	#first check the originalsent of $sentid starts with a [a-z\d]
	$sth = $dbh->prepare("select originalsent from ".$dataprefix."_sentence where sentid = $sentid");
	$sth->execute() or warn "$sth->errstr\n";
	($thissent) = $sth->fetchrow_array(); #take the tag of the first sentence
	if($thissent =~/^\s*[^A-Z]/){
		#for the following regexp to work, need to change originalsent's collate to latin1_general_cs (cs for case sensitive)
		$sth = $dbh->prepare("select modifier, tag from ".$dataprefix."_sentence where (tag != 'ignore' or isnull(tag)) and (originalsent COLLATE utf8_bin regexp '^[A-Z].*' or originalsent rlike ': *\$') and sentid < $sentid order by sentid desc");
		$sth->execute() or warn "$sth->errstr\n";
		($modifier, $tag) = $sth->fetchrow_array(); #take the tag of the first sentence
		$tag = $modifier." ".$tag if $modifier =~/\w/;
		$tag =~ s#[\[\]]##g;
	}
	#print "find parent tag: ".$tag!~ /\w/? "[parenttag]\n" : "[".$tag."]\n";
	return $tag!~ /\w/? "[parenttag]" : "[".$tag."]";
}

#return ($m, $t), the last word in $tag becomes new $t, the rest $m, both in []
sub getpartsfromparenttag{
	my $tag = shift;
	if($tag =~/\[/){
		$tag =~ s# (\w+\])$#][\1#;
		$tag =~ /(\[.*?\])?(\[.*?\])/;
		return ($1, $2);
	}else{
		$tag =~ /(.*?)\s*\b(\w+)$/;
		return ($1, $2);
	}	
}

############################################################################################
########################and/or compound subjects           #################################
############################################################################################
#deal with "herbs or lianas" cases
#examples:
#<cypsela_or_palea_unit> cypsela / palea unit � obovate, 2 . 5 � 4 mm</cypsela_or_palea_unit>
#<biennial_or_short_lived_perennial> biennials or short_lived , usually monocarpic perennials , 10 � 100 cm ; cf: 
#<biennial_or_monocarpic_perennial> biennials or monocarpic perennials , 100 � 220 cm ;
#<(leaf) blade_or_lobe> leaf blades or lobes orbiculate to linear , 1 � 5 � 1 � 5 mm .
#<(outer)floret> and (3) outer florets pistillate, ???
#staminate or bisexual paleae readily falling , ( 1 � ) 3 � 5 , erect to apically somewhat spreading or incurved in fruit , slightly surpassing pistillate paleae ;
#annuals , biennials , or short_lived perennials , 20 � 100 cm .
#deep_seated woody tap_roots and caudices .

#patterns: b: boundary; n:structures; m:modifier; &:and/or//; u:unknown words

#M?N[b$]          #((?:m,?)*&?m)?((?:[np],?)*&?[np])($|b)  
#M: m | m,m,&m    #(m,?)*&?m
#N: n | n,n,&n    #(n,?)*&?n
#n: known n or pos=pl. #[np]

#e.g:
#<b></b> and/or// <b></b>: leave it for ditto to process
# <m> pl. and/or// pl. <b>: pls =>[p], pl. and/or pl. =>tag, <m> =>modifier
#<m> <n> and/or// xxx <b>?: xxx => [psn], <n> and/or xxx =>tag, <m> =>modifier
#<m> or <m> <n> <b> => <n> => tag, <m> and/or <m> =>modifier

#may infer new structures, modifiers, or boundary words
#return a positive number if new discoveries are made


sub andor{
	my ($sth1, $sentid, $sentence,  $sign);
	#my ($mptn, $nptn, $bptn, $sptn, $wptn);
	
	#(?:(?:((?:[mq],?&?)*(?:m|q(?=p)))?)((?:[np],?&?)*[np]),?)*&?(?:((?:[mq],?&?)*(?:m|q(?=p)))?)((?:[np],?&?)*[np])([,;:.]?$|,?b|,?(?<=p)q)
	print "\nto match pattern $ANDORPTN\n" if $debug;
	
	$sth1 = $dbh->prepare("select sentid, sentence from ".$dataprefix."_sentence where tag ='andor' ");
	$sth1->execute();
	while(($sentid, $sentence)=$sth1->fetchrow_array()){
		$sign += andortag($sentid, $sentence, $SEGANDORPTN, $ANDORPTN);# if isandorsentence($sentence);
	}
}

#S | S,S,&S  #a Segment is a noun phrase invovling a structure: eg. mp,mp,&mp
#S: M?N[b$]          #((?:m,?)*&?m)?((?:[np],?)*&?[np])($|,?b)  #((?:[mq],?)*&?[mq])?((?:[npq],?)*&?[npq])($|,?[bq])
#M: m | m,m,&m    #(m,?)*&?m
#N: n | n,n,&n    #(n,?)*&?n

#n: known n or pos=pl. or unknown [q] #[npq]
#m: known or q              #[mq]
#b: known or q              #[bq]

#must have at least a [np] and an &.
#return a positive number if new poss are discovered
sub andortag{
	my ($sentid, $sentence, $sptn, $wptn) = @_; #$sentence that makes isandorsentence true
	my ($sign, $modifier, $tag, @words, @wordscopy, $ptn, $sth2, $l, $token, $limit);
	my (@mptns, @msegs, @sptns, @ssegs);
	$token = "(and|or|nor|\/|and \/ or)";
	$limit = 80;
	@words = split(/\s+/, $sentence);
	$ptn = sentenceptn($token, $limit, @words);
	$ptn =~ s#t#m#g; #ignore the distinction between type modifiers and modifiers

	splice(@words, $limit);
	@wordscopy = @words; #@words matches $ptn;
	

	print "\nAndor pattern $ptn for @words\n" if $debug;

	if($ptn =~/$wptn/){
		#extract matched parts: 
		my $earlygroupsptn = substr($ptn, 0, $-[1]);
		#my @ptns = split("\s*,\s*",$earlygroupsptn);
		my @ptns = split(/\s*<B>,<\/B>\s*/,$earlygroupsptn); #3/28/09
		my $earlygroupswords = join (" ", splice(@wordscopy, 0, $-[1]));
		#my @segs = split("\s*,s*", $earlygroupswords); 
		my @segs = split(/\s*<B>,<\/B>s*/, $earlygroupswords); #3/28/09
		
		@wordscopy = @words;
		my $secondlastmodifierptn = $1;
		my $secondlastmodifierwords = join (" ", splice(@wordscopy, $-[1], ($+[1]-$-[1]))); 
		
		@wordscopy = @words;
		my $secondlaststructureptn = $2;
		my $secondlaststructurewords = join (" ", splice(@wordscopy, $-[2], ($+[2]-$-[2]))); 
		
		@wordscopy = @words;
		my $lastmodifierptn = $3;
		my $lastmodifierwords = join (" ", splice(@wordscopy, $-[3], ($+[3]-$-[3]))); 
		
		@wordscopy = @words;
		my $laststrcutureptn = $4;
		my $laststrcuturewords = join (" ", splice(@wordscopy, $-[4], ($+[4]-$-[4]))); 
		
		@wordscopy = @words;
		my $endsegementptn = $5;
		my $endsegementwords = join (" ", splice(@wordscopy, $-[5], ($+[5]-$-[5]))); 
		my $bindex = $-[5];
	
		#matching $ptn with original text:
		for(my $i = 0; $i <@ptns; $i++){ #early segs before the second last one. Perl doesn't capture those.
			if($ptns[$i] =~ /$sptn/){
				#push(@mptns, $1);
				#push(@msegs, substr($segs[$i], 0, $-[1]));
				#push(@sptns, $2);
				#push(@ssegs, substr($segs[$i], $+[1]));
				#5/18/09 check 8190
				push(@mptns, $1);
				push(@sptns, $2);
				my @w = split(/ /,$segs[$i]);
				my @wc = @w;
				my $m = join(" ", splice(@w, 0, $+[1]));
				if($m =~/\b(although|but|when|if|where)\b/){#5/18/09 $CONNECTORS
					return;
				}
				push(@msegs,$m);
				push(@ssegs,join(" ", splice(@wc, $+[1])));
			}else{
				die "wrong segment: $ptns[$i]=>$segs[$i]\n";
			}
		}		
		push(@mptns, $secondlastmodifierptn);
		push(@msegs, $secondlastmodifierwords);
		push(@sptns, $secondlaststructureptn);
		push(@ssegs, $secondlaststructurewords);
		push(@mptns, $lastmodifierptn);
		push(@msegs, $lastmodifierwords);
		push(@sptns, $laststrcutureptn);
		push(@ssegs, $laststrcuturewords);
		
		#find the modifier and the tag for $sentid
		if(structurecount(@sptns)>1){ #compound subject involving multiple structures: mn,mn,&mn => use all but bounary as the tag, modifier="";
			$tag = join(" ", splice(@words, 0, $bindex));
			$modifier = "";
			$tag =~ s#<\S+?>##g;
			if($tag =~/\b$token\b/){
				my $conj = $1;
				$tag =~ s#,# $conj #g;
				$tag =~ s#\s+# #g;
				$tag =~ s#($conj )+#\1#g;
				$tag =~ s#^\s+##g;
				$tag =~ s#\s+$##g;
				tagsentwmt($sentid, $sentence, "", $tag, "andor[n&n]");
				#$sth2 = $dbh->prepare("update ".$dataprefix."_sentence set tag = '$tag', modifier = '' where sentid = ".$sentid);
				#$sth2->execute();
				#print "Andor determine the tag [$tag] and modifier [$modifier] for: $sentence\n" if $debug;
			}else{
				print "Andor can not determine a tag or modifier for $sentid: $sentence\n" if $debug;
			}
		}elsif(structurecount(@sptns) == 1){#m&mn => connect all modifiers as the modifier, and the n as the tag
			my $i = 0;
			for($i = 0; $i < @sptns; $i++){
				if($sptns[$i] =~/\w/){last;}
			}
			$tag = $ssegs[$i];
			$tag =~ s#<\S+?>##g;
			$modifier = join(" ", @msegs);
			$modifier =~ s#<\S+?>##g;
			$tag =~ s#^\s+##g;
			$tag =~ s#\s+$##g;
			$modifier =~ s#^\s+##g;
			$modifier =~ s#\s+$##g;
			my $mystop = $stop; #3/26/09
			$mystop =~ s#\b$token\b##g;#3/26/09
			$mystop =~ s#\|+#|#g;
			if($modifier =~/\b$token\b/ && $modifier !~ /\b($mystop|to)\b/){#3/26/09 $stop => $mystop
				my @twords = split(/\s+/, $tag);
				$modifier .= " ".join (" ", splice(@twords, 0 , @twords-1)); 
				$tag = $twords[@twords-1];
				tagsentwmt($sentid, $sentence, $modifier, $tag, "andor[m&mn]");
				#$sth2 = $dbh->prepare("update ".$dataprefix."_sentence set tag = '$tag', modifier = '$modifier' where sentid = ".$sentid);
				#$sth2->execute();
				#print "Andor determine the tag [$tag] and modifier [$modifier] for: $sentence\n" if $debug;
			}else{
				print "Andor can not determine a tag or modifier for $sentid: $sentence\n" if $debug;
			}
		}else{
			print "Andor can not determine a tag or modifier for $sentid: $sentence\n" if $debug;
		}

		
		#discoveries in the end segment (boundary segment) can be dealt with now: the q in it is a b
		my $q = index($endsegementptn, "q");
		my $newboundaryword = (split(" ",$endsegementwords))[$q] if $q >=0;
		$sign += update($newboundaryword, "b", "", "wordpos", 1) if $newboundaryword =~ /\w/;	
		
		#discoveries from @mptns, @msegs, @sptns, @ssegs etc.
		#modifier patterns and segments:	$mptn = "((?:[mq],?)*&?(?:m|q(?=p)))";#grouped #may contain q but not the last m, unless it is followed by a p
		#mark all qs "m"
		
		#commented out: terms identified in text are more of a b/n word than m word
		#for(my $i = 0; $i <@mptns; $i++){
		#	$mptns[$i] =~ s#(.)#\1 #g;   #one modifier pattern
		#	chop($mptns[$i]);
		#	my @ps = split(/ /, $mptns[$i]);
		#	my @ts = split(/\s+/, $msegs[$i]);#matching segment pattern
		#	for(my $j = 0; $j < @ps; $j++){
		#		if($ps[$j] eq "q"){
		#			$ts[$j] =~ s#^\s+##g;
		#			$ts[$j] =~ s#\s+$##g;
		#			$sign += update($ts[$j], "m", "", "modifiers");
		#		}
		#	}
		#}
		
		#structure patterns and segments:	$nptn = "((?:[np],?)*&?[np])"; #grouped #must present, no q allowed		
		#mark all ps "p"
		for(my $i = 0; $i <@sptns; $i++){
			$sptns[$i] =~ s#(.)#\1 #g;   #one modifier pattern
			chop($sptns[$i]);
			my @ps = split(/ /, $sptns[$i]);
			my @ts = split(/\s+/, $ssegs[$i]);#matching segment pattern
			for(my $j = 0; $j < @ps; $j++){
				if($ps[$j] eq "p"){
					$ts[$j] =~ s#^\s+##g;
					$ts[$j] =~ s#\s+$##g;
					$sign += update($ts[$j], "p", "-", "wordpos", 1); #6/11/09 add "-"
				}
			}
		}	
	}elsif($ptn =~ /^b+&b+[,:;\.]/){ #deal with b+&b+[,:;\.] =>ditto
			tagsentwmt($sentid, $sentence, "", "ditto", "andor");
			#$sth2 = $dbh->prepare("update ".$dataprefix."_sentence set tag = 'ditto', modifier = '' where sentid = ".$sentid);
			#$sth2->execute();
			#print "Andor determine the tag [ditto] and modifier [] for: $sentence\n" if $debug;
	}else{
			print "Andor can not determine a tag or modifier for $sentid: $sentence\n" if $debug;
	}
	return $sign;
}

#<b>,</b> => ,
sub sentenceptn{
	my ($token, $limit, @words) = @_;
	my($l, $ptn, $typemodifierptn);
	$typemodifierptn = gettypemodifierptn();
	$l = 0; 
	foreach (@words){
		if(++$l > $limit){last;}
		if($_ =~ /\b$token\b/){
	   		$ptn .="&";
	 	}else{
	 		if(/([,:;\.])/){
	 			$ptn .=$1;
	 			#$ptn .= $1 eq "."? "\\.": $1;
	 		}elsif(/<(\w)>/){
	 			my $tag = $1;
	 			if($tag eq "M" && $_ =~/\b($typemodifierptn)\b/){
	 				$ptn .="t";
	 			}else{	 			
	 				$ptn .= lc $tag; #N => P???
	 			}	 			
	 		} #take the outermost tag :<m><b></b></m>: take m if $_ appears ????
	   		#elsif(/^([,:;\.])$/){$ptn.=lc $1; $ptn = "\\".$ptn if $ptn eq ".";}
	   		elsif(getnumber($_) eq "p"){$ptn.="p";}
	   		else{$ptn.="q";} #a question mark.
	 	}
	} 
	return $ptn;
}

sub structurecount{
	my @ptn = @_;
	my $count = 0;
	foreach (@ptn){
		if (/\w/){
			$count++;
		}
	}
	return $count;
}

############################################################################################
######################## comma used for 'and'          #################################
############################################################################################
#seen in TreatiseH, using comma for 'and' as in "adductor , diductor scars clearly differentiated ;"
#which is the same as "adductor and diductor scars clearly differentiated ;"
#^m*n+,m*n+ or m*n+,m*n+;$, or m,mn+
#clauses dealt in commaand do not contain "and/or". andortag() deals with clauses that do.
sub commaand{ 
	my ($sth, $sentid, $sentence, $tag, $ptn, $ptn1, $ptn2, $ptn3);
	#4/26/09 cover m,mn
	my $nphraseptn = "(?:<[A-Z]*[NO]+[A-Z]*>[^<]+?<\/[A-Z]*[NO]+[A-Z]*>\\s*)+"; #4/26/09: last + =>*
	my $mphraseptn = "(?:<[A-Z]*M[A-Z]*>[^<]+?<\/[A-Z]*M[A-Z]*>\\s*)"; #5/1/09, add last \\s*
	my $bptn = "(?:<[A-Z]*B[A-Z]*>[,:\.;<]<\/[A-Z]*B[A-Z]*>)";
	my $commaptn = "<B>,</B>";
	print "::::::::::::::::::::::In commaand:\n";
	
	my $phraseptn = $mphraseptn."*\\s*".$nphraseptn;#4/26/09 put in \s
	$ptn = $phraseptn."\\s+".$commaptn."\\s+(?:".$phraseptn."| |".$commaptn.")+";
	$ptn1 = "^(".$ptn.")";
	$ptn2 = "(.*?)(".$ptn.")\\s*".$bptn."\$";
	$ptn3 = "^((?:".$mphraseptn."\\s+)+".$commaptn."\\s+(?:".$mphraseptn."|\s*|".$commaptn.")+".$mphraseptn."+\\s*".$nphraseptn.")"; #changed last * to + 5/01/09 check 5606
	#changed | | to |\s*|, check 3723
	
	#my $nphraseptn = "(?:<[A-Z]*[NO]+[A-Z]*>[^<]+?<\/[A-Z]*[NO]+[A-Z]*>)+"; #4/26/09: last + =>*
	#my $mphraseptn = "(?:<[A-Z]*M[A-Z]*>[^<]+?<\/[A-Z]*M[A-Z]*>)*";
	#my $bptn = "(?:<[A-Z]*B[A-Z]*>[,:\.;<]<\/[A-Z]*B[A-Z]*>)";
	#print "::::::::::::::::::::::In commaand:\n";
	
	#my $phraseptn = $mphraseptn.$nphraseptn;#4/26/09 put in \s
	#$ptn = $phraseptn."\\s*<B>,</B>\\s*(?:".$phraseptn."| |,)+";
	
	my $q = "select sentid, sentence from ".$dataprefix."_sentence"; 
	$sth = $dbh->prepare($q);
	$sth->execute() or warn "$sth->errstr\n";
		
	while(($sentid, $sentence) = $sth->fetchrow_array()){
		my $sentcopy = $sentence; #4/26/09 check sentid 6791, 318, 1494, 8055 for effect
		$sentcopy =~ s#></?##g;
		if($sentcopy =~ /$ptn1/){
			$tag = $1;
			$tag =~ s#,#and#g;
			$tag =~ s#</?\S+?>##g;
			$tag =~ s#(^\s+|\s+$)##g;
			if($tag !~/ and$/){
				print "commaand[CA1:$ptn1]: \n" if $debug;
				tagsentwmt($sentid, $sentence, "", $tag, "commaand[CA1]");
			}
		}elsif($sentcopy =~/$ptn2/){#4/26/09 put in \s
			$tag = $2;
			if($1!~/\b($PROPOSITION)\b/ and $1 !~/<N>/){ #add 2nd condition 5/01/09 check 8979
				$tag =~ s#,#and#g;
				$tag =~ s#</?\S+?>##g;
				$tag =~ s#(^\s+|\s+$)##g;
				if($tag !~/ and$/){
					print "commaand[CA2:$ptn2]: \n" if $debug;
					tagsentwmt($sentid, $sentence, "", $tag, "commaand[CA2]");
				}
			}
		}elsif($sentcopy =~/$ptn3/){#4/26/09 m,mn+
		#$mphraseptn\s*$commaptn\s*(?:$mphraseptn| |$commaptn)+
			$tag = $1;
			if($1!~/\b($PROPOSITION)\b/){
				$tag =~ s#,#and#g;
				$tag =~ s#</?\S+?>##g;
				$tag =~ s#(^\s+|\s+$)##g;
				if($tag !~/ and$/){
					print "commaand[CA3:$ptn3]: \n" if $debug;
					my @tagwords = split(/\s+/, $tag);
					$tag = $tagwords[@tagwords-1];
					my $modifier = join(" ", splice(@tagwords, 0, @tagwords-1));
					tagsentwmt($sentid, $sentence, $modifier, $tag, "commaand[CA3]");
				}
			}
		}
	}
}
############################################################################################
######################## unknownwordbootstrapping          #################################
############################################################################################
#based on "m o b" pattern

sub unknownwordbootstrapping{
	my ($sth,$sth1, $word,$sentence,$sentid, $o, $b, $m);
	#my ($sth,$sth1, $word,$sentence,$sentid, $o, $b);
	
	my $plmiddle = "(ee)";
	tagallsentences("singletag", "sentence"); #raw tag, apply all tags
	my $new = 0;
	#5/31/09: why limit to isnull(tag) or tag!="ignore"? "borders" sentid = 3582.
	do{
		$new = 0;
		$o = ""; $b = ""; 
		$m = "";
		#my $q = "select word from ".$dataprefix."_unknownwords where flag ='unknown' and (word rlike '($PLENDINGS)\$' or word rlike '$plmiddle' )";
		my $q = "select word from ".$dataprefix."_unknownwords where flag ='unknown' and (word rlike '($PLENDINGS|ium)\$' or word rlike '$plmiddle' )"; #3/21/09
		$sth = $dbh->prepare($q);
		$sth->execute() or warn "$sth->errstr\n";
		
		while(($word) = $sth->fetchrow_array()){
			#if $word <b> pattern is seen in tagged sentences, yes, $word is a pl and o. update pl.
			#save pl words in an array
			if($word =~ /ium$/ && $word ne "medium"){#block added 3/21/09
				update($word,"s", "-", "wordpos", 1);
				$o .= $word."|" if ($o !~ /\b$word\b/ && $word !~/\b($FORBIDDEN)\b/);
				print "unknownwordbootstrapping: find a [s] $word\n" if $debug;
			}else{
				$sth1 = $dbh->prepare("select * from ".$dataprefix."_sentence where (tag != 'ignore' or isnull(tag)) and sentence rlike '(^| )$word (<B>|$FORBIDDEN)'"); #covers <b>[punct]
				$sth1->execute() or warn "$sth1->errstr\n";
				if($sth1->rows() >= 1 && getnumber($word) eq "p" && ! verbending($word)){
					update($word,"p", "-", "wordpos", 1);
					$o .= $word."|" if ($o !~ /\b$word\b/ && $word !~/\b($FORBIDDEN)\b/);
					print "unknownwordbootstrapping: find a [p] $word\n" if $debug;
				}
			}
		}
		
		chop($o);
		
		#then find $word <q> and make q a b
		if($o =~/\w/){
			$sth = $dbh->prepare("select sentence from ".$dataprefix."_sentence where (tag != 'ignore' or isnull(tag)) and sentence rlike '(^| )($o) [^<]'");
			$sth->execute() or warn "$sth->errstr\n";
			
			while(($sentence) = $sth->fetchrow_array()){
				if($sentence =~ /\b($o) (\w+)/){
					my $t = $2;
					update($t,"b", "", "wordpos", 1);
					$b .= $t."|" if ($b !~ /\b$t\b/ && $t !~/\b($FORBIDDEN)\b/);
					print "unknownwordbootstrapping: find a [b] $t\n" if $debug;
				}
			}
			chop($b);
		
		
			#then find <q> $word, and make q a modifier
		
			$sth = $dbh->prepare("select sentence from ".$dataprefix."_sentence where (tag != 'ignore' or isnull(tag)) and sentence rlike '[^<]+ ($o) '");
			$sth->execute() or warn "$sth->errstr\n";
			
			while(($sentence) = $sth->fetchrow_array()){
				if($sentence =~ /(^|,<\/b>)([\w ]*?) ($o)\b/){
					my $t = $2;
					if($t !~ /\b($FORBIDDEN)\b/){ #annuals or biennials
						my @t = split(/\s+/, $t);
						if(@t<=2){
							foreach (@t){
								update($_,"m", "", "modifiers", 1);
								$m .= $_."|" if ($m !~ /\b$_\b/ && $_ !~/\b($FORBIDDEN)\b/);
								print "unknownwordbootstrapping: find a [m] $_\n" if $debug; #update modifier
							}
						}
					}
				}
			}
			chop($m);
		}

		my $all = $o."|".$b."|".$m;
		#my $all = $o."|".$b;
		$all =~ s#\|+#|#g;
		$all =~ s#\|$##g;
		my @all = split(/\|/, $all);
		$new = @all;
		
		#update the tagging of relevant sentences.
		if($new>0 && $all=~/\w/){
			my $q= "select sentid, sentence from ".$dataprefix."_sentence where (tag != 'ignore' or isnull(tag)) and sentence rlike '(^| )($all) '";
			#print stderr "$q\n";
			$sth = $dbh->prepare($q);
			$sth->execute() or warn "$sth->errstr\n";
			
			while(($sentid, $sentence) = $sth->fetchrow_array()){
				#$sentence = annotateSent($sentence, "", $o, $m, $b, "");
				$sentence = annotateSent($sentence, "", $o, "", $b, "");
				$sth1 = $dbh->prepare("update ".$dataprefix."_sentence set sentence ='$sentence' where sentid =".$sentid);
				$sth1->execute() or warn "$sth1->errstr\n";
			}	
		}
	}while($new > 0);
	
	#pistillate_zone
	my $q = "select word from ".$dataprefix."_wordpos where pos in ('s','p')";
	$sth = $dbh->prepare($q);
	$sth->execute() or warn "$sth->errstr\n";
	my $nouns = "";
	while(($word) = $sth->fetchrow_array()){
		$nouns .=$word."|";
	} 
	chop($nouns);

	$b = "";	
	$q = "select word from ".$dataprefix."_unknownwords where flag ='unknown' and word like '%\\_%'";
	$sth = $dbh->prepare($q);
	$sth->execute() or warn "$sth->errstr\n";
	while(($word) = $sth->fetchrow_array()){
		if($word !~ /_($nouns)$/i){
			$b .=$word."|" if ($b !~ /\b$word\b/ && $word !~/\b($FORBIDDEN)\b/);
			update($word, "b", "", "wordpos", 1);
		}
	} 
	chop($b);
	
	if($b=~ /\w/){
		$sth = $dbh->prepare("select sentid, sentence from ".$dataprefix."_sentence  where (tag != 'ignore' or isnull(tag)) and sentence rlike '(^| )($b) '");
		$sth->execute() or warn "$sth->errstr\n";
			
		while(($sentid, $sentence) = $sth->fetchrow_array()){
				$sentence = annotateSent($sentence, "", "", "", $b, "");
				$sth1 = $dbh->prepare("update ".$dataprefix."_sentence set sentence ='$sentence' where sentid =".$sentid);
				$sth1->execute() or warn "$sth1->errstr\n";
		}	
	}
}

sub verbending{
	my $pword = shift;
	my $sword = singular($pword);
	
	if($sword=~/e$/){
		chop($sword);
	}elsif($sword=~/([^aeiou])$/){
		$sword.=$1.'?';
	}
	
	$sword = '(^|_)'.$sword."ing";
	my $sth = $dbh->prepare("select word from ".$dataprefix."_unknownwords where word rlike '$sword\$' ");
	$sth->execute() or warn "$sth->errstr\n";
	if($sth->rows() > 0){
		print "for unknownwordbootstrapping: found verb ending words with the same root as $pword\n" if $debug;
		return 1;
	}
	return 0;
}

############################################################################################
######################## resolvesentencetags               #################################
############################################################################################

#determine the role for each word given the context in which it occurs
#all sentence in sentence table are tagged, all sentences have the same content as originalsents. 
#some words tagged with multiple tags in o n m b order
sub resolvesentencetags{
	my ($sth, $sth1, $sentid, $sentence, $typemodifiers, $word);
	untagsentences();
	tagallsentence("multitags");
	
	$sth = $dbh->prepare("select word from ".$dataprefix."_modifiers where istypemodifier = 1");
	$sth->execute() or warn "$sth->errstr\n";
	while(($word) = $sth->fetchrow_array()){
		$typemodifiers .= $word."|";
	}
	chop($typemodifiers);
	
	$sth = $dbh->prepare("select sentid, sentence from ".$dataprefix."_sentence where (tag != 'ignore' or isnull(tag))");
	$sth->execute() or warn "$sth->errstr\n";
	
			
	while(($sentid, $sentence) = $sth->fetchrow_array()){
		$sentence =~ s#><##g;
		$sentence =~ s#></##g; #<M><B>xxx</M></B> => <MB>xxx</MB>
		$sentence = filteredbycontext($sentence, $typemodifiers);
		$sth1 = $dbh->prepare("update ".$dataprefix."_sentence set sentence ='$sentence' where sentid =".$sentid);
		$sth1->execute() or warn "$sth1->errstr\n";
	}	
}

# o, n, m, b; on, om, ob, nm, nb, mb; onm, nmb; onmb
#the roles are m, b, o: typical syntax = [m* o b*], or [b* m* o].
#need to determine a role for each word
sub filteredbycontext{
	my ($sent, $typemodifiers) = @_;
	print "bycontext: $sent\n" if $debug;
	my ($newsent);
	$newsent = "";
	#resolve 1-tag cases 
	#when tag is [on]. A n could play either a o or a m role
	while($sent =~ /(.*?)(<[ON]>\w+<\/[ON]>)( .*)/){
		my $pre = $1;
		my $mid = $2;
		my $post = $3;
		if($post=~/^\s*<\w*B/){ #n => o
			$mid =~ s#<[ON]>#<O>#g;
			$mid =~ s#</[ON]>#</O>#g;
			$newsent .= $pre.$mid;
		}elsif($post=~/^\s*<\w*[ON]/){ #n => m
			$mid =~ s#<[ON]>#<M>#g;
			$mid =~ s#</[ON]>#</M>#g;
			$newsent .= $pre.$mid;
		}
		$sent = $post;
	}
	$newsent .= $sent;
	
	$sent = $newsent;
	$newsent = "";
	#when tag is b. A b could play either a m or a b role
	while($sent =~ /(.*?)(<B>\w+<\/B>)( .*)/){
		my $pre = $1;
		my $mid = $2;
		my $post = $3;
		if(($pre=~/<\/\w*M>\s*/ || $pre =~/,(?:<\/B>)?\s*/) && $post=~/^\s*<\w*[ONM]/ ){ #b => m when mb[onm] or ,b[onm]
			$mid =~ s#<B>#<M>#g;
			$mid =~ s#</B>#</M>#g;
		}
		$newsent = $pre.$mid;
		$sent = $post;
	}
	$newsent .= $sent;
	
	$sent = $newsent;
	$newsent = "";
	#when tag is m. A m could play either a m or a o [type modifier] role
	while($sent =~ /(.*?)(<M>\w+<\/M>)( .*)/){
		my $pre = $1;
		my $mid = $2;
		my $post = $3;
		#if a m is a tm and followed by a b [the last tm is treated as an o: principal cauline will be a m o]
		if(($pre=~/<\/\w*M>\s*/ || $pre =~/,(?:<\/B>)?\s*/) && $post=~/^\s*<\w*B/ ){ #m => o when mb
			if($mid =~ /<M>(\w+)<\/M>/){
				my $t = $1;
				if($t =~/\b($typemodifiers)\b/){
					$mid =~ s#<M>#<O>#g;
					$mid =~ s#</M>#</O>#g;
				}
			}
		}
		$newsent .= $pre.$mid;
		$sent = $post;
	}
	$newsent .= $sent;
	
	#resolve two-tag cases
	while($sent =~ /(.*?)(<\w{2,2}>\w+<\/\w{2,2}>)( .*)/){
		my $pre = $1;
		my $mid = $2;
		my $post = $3;
		if($mid =~ /<on>(\w+)</){ #<on> could be either <o> or <m>
			my $word = $1;
			
			$mid = "<n>".$1."</n>";
			$sent = $pre.$mid.$post;
		}elsif($mid =~ /<o><m>(\w+)</){ #seen used as o and m, but not a noun, not a boundry.
			$mid = "<n>".$1."</n>";
			$sent = $pre.$mid.$post;
		}elsif($mid =~ /<o><b>(\w+)</){
			$mid = "<n>".$1."</n>";
			$sent = $pre.$mid.$post;
		}elsif($mid =~ /<n><m>(\w+)</){
			$mid = "<n>".$1."</n>";
			$sent = $pre.$mid.$post;
		}elsif($mid =~ /<n><b>(\w+)</){
			$mid = "<n>".$1."</n>";
			$sent = $pre.$mid.$post;
		}elsif($mid =~ /<m><b>(\w+)</){
			$mid = "<m>".$1."</m>";
			$sent = $pre.$mid.$post;
		}
	}
	
	#resolve three-tag cases
	while($sent =~ /(.*?)(<\w{3,3}>\w+<\/\w{3,3}>) (.*)/){
		my $pre = $1;
		my $mid = $2;
		my $post = $3;
		#onm, nmb;
		if($mid =~ /<o><n><m>(\w+)</){
			$mid = "<n>".$1."</n>";
			$sent = $pre.$mid.$post;
		}elsif($mid =~ /<n><m><b>(\w+)</){
			$mid = "<n>".$1."</n>";
			$sent = $pre.$mid.$post;
		}
	}
	
	#resolve four-tag cases
	while($sent =~ /(.*?)(<\w{4,4}>\w+<\/\w{4,4}>) (.*)/){
		my $pre = $1;
		my $mid = $2;
		my $post = $3;
		#onmb
		if($mid =~ /<o><n><m><b>(\w+)</){
			$mid = "<n>".$1."</n>";
			$sent = $pre.$mid.$post;
		}
	}
return $sent;
}


############################################################################################
######################## ditto                             #################################
############################################################################################
#makes no new discoveries
#tag clauses that start with b words ditto. 
#in FNA: If a clause contain another ,-separated clause, it is possible for that clause to have a noun subject.
#in Treatise: There are non-ditto clauses with comma-separated b/m modified subjects.===>How to distinguish these two cases?  
sub ditto{
	#3/24/09
	my ($sth, $sentid, $sentence, $head, $modifier, $tag, $sth2);

	$sth = $dbh->prepare("select sentid, sentence from ".$dataprefix."_sentence where isnull(tag)");
	$sth->execute() or  warn "$sth->errstr\n"; 
		
	while(($sentid, $sentence) = $sth->fetchrow_array()){ #$sentence is tagged with <[mbn]>
		 print "\nIn Ditto for: [$sentid] $sentence\n" if $debug;
		 #3/24/09 markup [^nosp]+, [m]*[nosp]+ [b] => ditto
		 #        markup [^nosp]+, [b]+[nosp]+ [b] => null for remainingnull markup

		 my $nphraseptn = "(?:<[A-Z]*[NO]+[A-Z]*>[^<]+?<\/[A-Z]*[NO]+[A-Z]*>\\s*)+";
		 my $mphraseptn = "(?:<[A-Z]*M[A-Z]*>[^<]+?<\/[A-Z]*M[A-Z]*>\\s*)+";
		 #my $bphraseptn = "(?:<[A-Z]*B[A-Z]*>[^,:\.;<]+?<\/[A-Z]*B[A-Z]*>\\s*)+";
		 my $sentcopy = $sentence;
		 $sentcopy =~ s#></?##g;
		 
		 
		 if($sentence !~ /<[NO]>/){#no nouns
		 	$tag = "ditto";
		 	tagsentwmt($sentid, $sentence, $modifier, $tag, "ditto-no-N");
		 #}elsif($sentcopy =~ /(.*?)$nphraseptn$bphraseptn/){ #contains nouns followed by <b>
		 }elsif($sentcopy =~ /(.*?)$nphraseptn/){ #contains nouns	
		 #print $sentcopy."\n";
		 	my $head = $1;
		 	if($head =~ /\b($PROPOSITION)\b/){ #nouns occurring after a proposition
		 		$tag = "ditto";
		 		tagsentwmt($sentid, $sentence, $modifier, $tag, "ditto-proposition");
		 	#}elsif($head =~ /$mphraseptn$/ && $head =~/,/ && $head =~/<B>\w+/){ #5/1/09 conflict with commaand#nouns occuring after some discussions of an ditto organ
		 	#	$tag = "ditto";
		 	#	tagsentwmt($sentid, $sentence, $modifier, $tag, "ditto-B-,-M-N");
		 	}elsif($head =~/,<\/B>\s*$/){#4/11/09: , n
		 	 	$tag = "ditto";
		 	 	tagsentwmt($sentid, $sentence, $modifier, $tag, "ditto-,-N");
		 	}#elsif($head =~/,/){#4/11/09
		 	 #	$tag = "ditto";
		 	 #	tagsentwmt($sentid, $sentence, $modifier, $tag, "ditto-comma-WO-M-N");
		 	#}
		 }
	}
	#before 3/24/09	 	
	#my ($sth, $sentid, $sentence, $bptn, $wptn, $ptn, $conj, $sth2);

	##$conj = "(?:[^a-z0-9<_]|\\b(?\:and|or|/|to)\\b)"; #� <b>distalmost</b> <b>reduced</b> , � <b>bractlike</b> .
	##$bptn = "\\s*(?:</?B>)\\s*";  #boundary pattern
	##$wptn = "(?:.*?)"; #text pattern
	##$ptn = "^".$conj."?".$bptn.$wptn.$bptn.$conj."+"; #sentence starts with at least 3 boundary words
	
	#$conj = "(?:\\b(?\:and|or|/|to)\\b)"; #� <B>distalmost</B> <B>reduced</B> <B>,</B> <B>�</B> <b>bractlike</b> <B>.</B>
	#$bptn = "\\s*(?:</?B>)\\s*";  #boundary pattern
	#$wptn = "(?:.*?)"; #text pattern
	#$ptn = "^".$conj."?".$bptn.$wptn.$bptn.$conj."*"; #sentence starts with at least 3 boundary words
	
	#$sth = $dbh->prepare("select sentid, sentence from ".$dataprefix."_sentence where isnull(tag)");
	#$sth->execute() or  warn "$sth->errstr\n"; 
		
	#while(($sentid, $sentence) = $sth->fetchrow_array()){ #$sentence is tagged with <[mbn]>
	#	my $count = 0;
	#	my $sentcopy = $sentence;
	#	print "\nin Ditto: for: [$sentid] $sentence\n" if $debug;
	#	while($sentence =~/$ptn(.*)/){
	#		$sentence = $1;
	#		$count++;
	#	}
	#	#sentence ends with a N => phraseclause
	#	if(($count > 2 and $sentence !~/<\/N>\S* <B>[\.,;:]<\/B>\s*$/) or $sentence !~/\w/){
	#		tagsentwmt($sentid, $sentcopy, "", "ditto", "ditto");
	#	}
	#}
}

############################################################################################
######################## phraseclause                      #################################
############################################################################################
#makes no new discoveries
# sentences (clauses) consist of one noun phrase: [modifier|bounary] structure [;.,]
sub phraseclause{ 
	my ($sth, $sentid, $sentence, $head, $modifier, $tag, $sth2);

	$sth = $dbh->prepare("select sentid, sentence from ".$dataprefix."_sentence where isnull(tag)");
	$sth->execute() or  warn "$sth->errstr\n"; 
		
	while(($sentid, $sentence) = $sth->fetchrow_array()){ #$sentence is tagged with <[mbn]>
		 print "\nIn Phraseclause for: [$sentid] $sentence\n" if $debug;
		 #3/21/09 relax conditions to accomendate [mb],[mb], [mb] [nsp] [,:\.;]
		 my $sentcopy = $sentence;
		 $sentcopy =~ s#></?##g;
		 
		 if($sentcopy =~ /^(.*?)((?:<[A-Z]*M[A-Z]*>[^<]*?<\/[A-Z]*M[A-Z]*>\s*)*)((?:<[A-Z]*[NO]+[A-Z]*>[^<]*?<\/[A-Z]*[NO]+[A-Z]*>\s*)+)<B>[,:\.;]<\/B>\s*$/){
		 	$head = $1;
		 	$modifier = $2;
		 	$tag = $3;
		 	if($head !~ /\b($PROPOSITION)\b/ && $head !~ /<\/N>/ && $modifier !~/\b($PROPOSITION)\b/){
		 		if($tag =~/(.*?)<N>([^<]+)<\/N>\s*$/){#take the last N as the tag
		 			$modifier .=" ".$1; #4/22/09 removed if $1=~/\w/ check sentid 6384 for effects
		 			$tag = $2;
		 		}
		 		$tag =~ s#<\S+?>##g;
		 		$modifier =~ s#<\S+?>##g;
		 		$tag =~s#(^\s*|\s*$)##g;
		 		$modifier =~ s#(^\s*|\s*$)##g;
		 		tagsentwmt($sentid, $sentence, $modifier, $tag, "phraseclause");
		 	}
		 }
		 	
		 #if($sentence =~ /^(.*?)((?:<[MB]>[^<]*?<\/[MB]>\s*)*)((?:<N>[^<]*?<\/N>\s*)+)<B>[,:\.;]<\/B>\s*$/){ #ends with a n
		 #	$head = $1;
		 #	$modifier = $2;
		 #	$tag = $3;
		 #	if($head !~ /\w+ / && $head !~ /<\/N>/ && $modifier !~/\b($PROPOSITION)\b/){
		 #		if($modifier =~ /<M>(\S+?)<\/M>\S*\s*$/){#take the last M as part of modifier
		 #			$modifier = $1;
		 #		}else{
		 #			$modifier = ""; 
		 #		}
		 #		if($tag =~/(.*?)<N>([^<]+)<\/N>\s*$/){#take the last N as the tag
		 #			$modifier .=" ".$1 if $1 =~/\w/;
		 #			$tag = $2;
		 #		}
		 #		$tag =~ s#<\S+?>##g;
		 #		$modifier =~ s#<\S+?>##g;
		 #		$tag =~s#(^\s*|\s*$)##g;
		 #		$modifier =~ s#(^\s*|\s*$)##g;
		 #		tagsentwmt($sentid, $sentence, $modifier, $tag, "phraseclause");
		 #	}
		 #}
	}
}

############################################################################################
######################## of                                #################################
############################################################################################

#dealing with x of y cases:
#1.	�sub-structure of structure� pattern: sub-structure string = "part|parts|area|areas|portion|portions"
#Example: blades of undivided cauline leaves oblong , ovate.
#Example: <(sterile [floret] corolla) lobe>corolla lobes of sterile 10 � 15 mm , spreading ;
#Example: <(bisexual floret) [corolla]> of bisexual florets pinkish. </(bisexual floret) [corolla]>

#2.	 �structure of sub-structure� pattern:
#Example: <calyculus>Calyculi of appressed bractlets.

#3.	�structure of count� pattern:
#Example: <calyculus>Calyculi of ca . 18 , reflexed to recurved
# <floret>Florets of 1 , 2 , or 3 + kinds in a head :</floret>

#4.	�clusters of structure� pattern
#Example: <root> clusters of fibrous root.</root>
#outer series of bristlelike scales , inner of plumose bristles . (made up of, case 2)
#Note: other �cluster� terms include �arrays� (as in �arrays of heads�. Cf: �heads in corymbiform arrays.�), �series�, etc.

#in order to distinct case 1 and 2, need knowledge about the hierarchy of structures.
#need records of adjectivesubjects (e.g. inner) to deal with cases such as  "apices of inner": modifiers table istypemodifier=1
sub of{
	my ($sth, $sentid, $modifier, $tag, $sentence,  $sth1, $originalsent);
	my($n, $o, $m, $b, $b1) = knowntags("singletag");
	#$CLUSTERSTRINGS = "clusters|cluster|arrays|array|series|fascicles|fascicle|pairs|pair";
	
	$sth = $dbh->prepare("select sentid, modifier, tag, sentence, originalsent from ".$dataprefix."_sentence where sentence like '%>of<%'"); #5/11/09: take out "(tag != 'ignore' or isnull(tag)) and" check 27, 256, 1476, 6829, 7203
	$sth->execute() or  warn "$sth->errstr\n"; 
	
	while(($sentid, $modifier, $tag, $sentence, $originalsent) = $sth->fetchrow_array()){
		if($sentence =~ /^[^N,]*(?:<[A-Z]>)?(?:$CLUSTERSTRINGS)(?:<\/[A-Z]>)? <B>of<\/B>(.*?(?:<N>[^<]+?<\/N>\s?)+)/){ #case 4 4/22/09 <B>two</B> <B>pairs etc. take case 4 out.
			#}elsif($sentence =~ /^(?:<[A-Z]>)?(?:$CLUSTERSTRINGS)(?:<\/[A-Z]>)? <B>of<\/B>(.*?(?:<N>[^<]+?<\/N>\s?)+)/){ #case 4
				my $tagphrase = $1;
				if($tagphrase =~ /.*?(<N>[^<]+<\/N> ?)+/){
					my $tag = $1;
					$tagphrase =~ s#$tag##;
					$modifier = $tagphrase;
					$modifier =~ s#<\S+?>##g;
					$tag =~ s#<\S+?>##g;
					$tag =~ s#(^\s*|\s*$)##g;
					$modifier =~ s#(^\s*|\s*$)##g;
					tagsentwmt($sentid, $sentence, $modifier, $tag, "Of[C4: clusters]");
					#$sth1 = $dbh->prepare("update ".$dataprefix."_sentence set tag = '$tag', modifier='$modifier' where sentid = $sentid");
					#$sth1->execute() or warn "$sth1->errstr\n";
					#print "In Of[C4: clusters]: change tag to <$tag>, modifier to <$modifier> to [$sentence]\n\n" if $debug;
				}
		}elsif($sentence =~/(.*?)(<N>[^<]*?<\/N> )+<B>of/ && $1 !~ /<N>/ ){ #select a of-clause #5/11/09 ()+ check 7211
				if($sentence =~/<N>([^<]*?)<\/N> <B>of<\/B> (?:[^M ]*?|<[A-Z]>ca<\/[A-Z] <B>.<\/B>) ?<B>\d.*?<N>([^<]*?)<\/N>/){ #case 3
					#keep the original tag and modifier
					updatesubstructure($2, $1);
					print "In Of[C3: follow by numbers]: no change to [$sentid: $sentence]\n\n" if $debug;
					print "In Of[C3: follow by numbers]: add substructure $2 to structure $1\n\n" if $debug;
				}else{ #case one and two: n of n pattern, which n is a substructure?
					$originalsent = lc $originalsent;
					$originalsent = annotateSent($originalsent,$n, $o, $m, $b, $b1);	
					caseonentwo($sentid, $modifier, $tag, $originalsent);				
				}
		}
	
	}
}

#return tm|tm|tm
#consider to replace this with truemodifiers() 4/15/09
sub gettypemodifierptn{
	my($sth, $type, $tm);
	$sth = $dbh->prepare("select word from ".$dataprefix."_modifiers where istypemodifier = 1");
	$sth->execute() or  warn "$sth->errstr\n";
	while(($tm)=$sth->fetchrow_array()){
		$type .=$tm."|";
	}
	return chop($type);
}


#establish part_of relationships among structures. 
#use reliable clues only

#case 1: substructure of structure => <(structure) substructure>
#case 2: structure [consists] of substructure => <structure>
sub caseonentwo{
	my ($sentid, $modifier, $tag, $originalsent) = @_;
	my ($sth, $struct1m, $struct1, $struct2m, $struct2, $substruct, $sth1, $ptn, @words, @wordscopy);

	$originalsent =~ s#<B>\(</B> <B>of</B>#<B>of</B>#g; #blades (of basal leaves) absent; => remove the ( before "of"
	$originalsent =~ s#<B>of</B>#of#g;
	#5/01/09 check words up to the first non-of proposition check sentid == 1469
	my $pcopy = $PROPOSITION;
	my $ocopy = $originalsent;
	$pcopy =~ s#of\|##;
	$ocopy =~ s#\s*(<[A-Z]>|\b)($pcopy)\b.*##;
	@words = split(/\s+/,$ocopy);
	#@words = split(/\s+/,$originalsent);
	$ptn = sentenceptn("(of)", 80, @words); 
	
	
	#if($ptn =~/^[bq]{0,2}?([mt]*)([tnp]+)&[bq]{0,2}?([mt]*)([tnp]+)/){ #struct1 of struct2
	if($ptn =~/^([bqmt]{0,4})([tnp]+)&([bqmt]{0,4})([tnp]+)/){	#5/01/09 change 3 to 4
		@wordscopy = @words;
		$struct1m = join (" ", splice(@wordscopy, $-[1], ($+[1]-$-[1]))); 
		@wordscopy = @words;
		$struct1 = join (" ", splice(@wordscopy, $-[2], ($+[2]-$-[2]))); 
		@wordscopy = @words;
		$struct2m = join (" ", splice(@wordscopy, $-[3], ($+[3]-$-[3]))); 
		@wordscopy = @words;
		$struct2 = join (" ", splice(@wordscopy, $-[4], ($+[4]-$-[4]))); 
		
		if($2 eq "t"){
			$struct1m .= " ".$struct1; 
			$struct1m =~ s#(^\s*|\s*$)##g;
			$struct1 = getparentsentencetag($sentid);
			if($struct1 eq "[parenttag]"){
				return;
			}
		}
		
		if($4 eq "t"){
			$struct2m .= " ".$struct2;
			$struct2m =~ s#(^\s*|\s*$)##g; 
			$struct2 = getparentsentencetag($sentid);
			if($struct2 eq "[parenttag]"){
				return;
			}
		}
		#5/11/09 check 715, 1005, 1535, 6228, 9442
		if($4 =~ /(.*?[np])t+$/){
			my $s = length($1);
			my @w = split(/\s+/,$struct2);
			$struct2 = join(" ", splice(@w, 0, $s));			
		}
		$struct1m =~ s#<\S+?>##g;
		$struct1 =~ s#(<\S+?>)##g;
		$struct2m =~ s#<\S+?>##g;
		$struct2 =~ s#(<\S+?>)##g;
		#5/01/09 added $SUBSTRUCTURESTRINGS
		if($struct1 =~ /\b($SUBSTRUCTURESTRINGS)\b/){
			$substruct = $struct1;
		}elsif($struct2 =~ /\b($SUBSTRUCTURESTRINGS)\b/){
			$substruct = $struct2;
		}else{
			$substruct = choosesubstructure($struct1m, $struct1, $struct2m, $struct2);
		}
		
		if($substruct =~/\w/ && $struct1 =~ /\b$substruct\b/){ #case one
			updatesubstructure($substruct, $struct2);
			#$modifier .= " ".$struct2m." ".$struct2; #5/01/09
			$modifier = $struct2m." ".$struct2." ".$modifier;#5/01/09 recheck FNA 19 secondary layer of shell => shell secondary layer. not secondary shell layer
			$modifier =~ s#\s+# #g;
			$modifier =~ s#(^\s*|\s*$)##g; 
			tagsentwmt($sentid, $originalsent, $modifier, $tag, "Of [C1:s1=$struct1 s2=$struct2 subs=$substruct]");
			#$sth1 = $dbh->prepare("update ".$dataprefix."_sentence set tag = '$tag', modifier='$modifier' where sentid = $sentid");
			#$sth1->execute() or warn "$sth1->errstr\n";
			#print "In Of [C1:s1=$struct1 s2=$struct2 subs=$substruct]: change tag to <$tag>, modifier to <$modifier> to [$originalsent]\n\n" if $debug;
		}else{ #case two or cannot determine substructure
			#do nothing
			updatesubstructure($substruct, $struct1) if $substruct =~/\w/;
			print "In Of[C2:s1=$struct1 s2=$struct2 subs=$substruct]: modifier is [$modifier], tag is <$tag>: no change to [$sentid][$originalsent]\n\n" if $debug;
		}
	}
}

sub updatesubstructure{
	my ($substruct, $struct) = @_;
	my ($sth);
	$substruct =~ s#<\$+?>##g;
	$substruct =~ s#(^\s*|\s*$)##g;
	$substruct =~ s#[\[\]]##g;
	$struct =~ s#<\$+?>##g;
	$struct =~ s#(^\s*|\s*$)##g;
	$struct =~ s#[\[\]]##g;
	return if $substruct !~ /\w/ or $struct !~ /\w/;
	$sth = $dbh->prepare("select * from ".$dataprefix."_substructure where substructure = '$substruct' and structure='$struct' ");
	$sth->execute() or  warn "$sth->errstr\n"; 
	if($sth->rows() >= 1){
		$sth = $dbh->prepare("update ".$dataprefix."_substructure set count = count+1 where substructure = '$substruct' and structure='$struct' ");
		$sth->execute() or  warn "$sth->errstr\n"; 
	}else{
		$sth = $dbh->prepare("insert into ".$dataprefix."_substructure values ('$struct', '$substruct', 1) ");
		$sth->execute() or  warn "$sth->errstr\n"; 	
	}
}
	
#use four types of evidience to determine which of the struct1 and struct2 is a substruct.
#evidence: 
#   n-n tags: (leaf) blades (== blades of leaf)
#	"with", 
#	clause-subclauses : ", <n>", 
#   sentence-clauses: "; <n>"	
#struct1 of struct2
sub choosesubstructure{	
	my ($struct1m, $struct1, $struct2m, $struct2) = @_;
	my ($sth, $modifier, $sentid, $tag, $sentence, $s1, $s2, $struct1r, $struct2r);
	
	#struct1 and 2 could be modified by another n, e.g margin hair of leaf blade
	#need only to determine the relationship between the first n of struct1 (margin) and last n of struct 2 (blade)
	#no need to consider other modifiers
	$struct1 =~ s#[\[\]]##g;
	$struct2 =~ s#[\[\]]##g;
	$struct1m =~ s#[\[\]]##g;
	$struct2m =~ s#[\[\]]##g;
	
	$s1 = $struct1;
	$s2 = $struct2;
	$struct1 =~ s#^(\w+) .*#\1#;
	$struct2 =~ s#^.* (\w+)$#\1#;
	
	#check substructure table
	$sth = $dbh->prepare("select * from ".$dataprefix."_substructure where substructure = '$struct1' and structure='$struct2' ");
	$sth->execute() or  warn "$sth->errstr\n"; 
	if($sth->rows() > 0){ return $s1;}
	
	$sth = $dbh->prepare("select * from ".$dataprefix."_substructure where substructure = '$struct2' and structure='$struct1' ");
	$sth->execute() or  warn "$sth->errstr\n"; 
	if($sth->rows() > 0){ return $s2;}
	
	#else looking for an answer from the corpus
	$struct1 = "\\\\[?".singularpluralvariations($struct1)."\\\\]?";
	$struct2 = "\\\\[?".singularpluralvariations($struct2)."\\\\]?";
	
	$struct1 =~ s#\|#\\\\]?|\\\\[?#g;
	$struct2 =~ s#\|#\\\\]?|\\\\[?#g;
	
	$struct1 = "(".$struct1.")";
	$struct2 = "(".$struct2.")";
	
	$struct1r = $struct1;
	$struct1r =~ s#\\\\\[\?#\\[?#g;
	$struct1r =~ s#\\\\\]\?#\\]?#g;
	
	$struct2r = $struct2;
	$struct2r =~ s#\\\\\[\?#\\[?#g;
	$struct2r =~ s#\\\\\]\?#\\]?#g;
	
	#check evidence 1: n-n tags
	my $q = "select * from ".$dataprefix."_sentence where (tag != 'ignore' or isnull(tag)) and (modifier rlike ' $struct1\$' or modifier rlike '^$struct1\$') and tag rlike '$struct2' ";
	$sth = $dbh->prepare($q);
	$sth->execute() or  warn "$sth->errstr\n"; 
	if($sth->rows() >= 1){
		return $s2;
	}
	
	$sth = $dbh->prepare("select * from ".$dataprefix."_sentence where (tag != 'ignore' or isnull(tag)) and (modifier rlike ' $struct2\$' or modifier rlike '^$struct2\$') and tag rlike '$struct1' ");
	$sth->execute() or  warn "$sth->errstr\n"; 
	if($sth->rows() >= 1){
		return $s1;
	}

	#check evidence 0: n-n phrase
	my $q = "select * from ".$dataprefix."_sentence where (tag != 'ignore' or isnull(tag)) and originalsent rlike '$struct1 $struct2' ";
	$sth = $dbh->prepare($q);
	$sth->execute() or  warn "$sth->errstr\n"; 
	if($sth->rows() >= 1){
		return $s2;
	}
	$q = "select * from ".$dataprefix."_sentence where (tag != 'ignore' or isnull(tag)) and  originalsent rlike '$struct2 $struct1' ";
	$sth = $dbh->prepare($q);
	$sth->execute() or  warn "$sth->errstr\n"; 
	if($sth->rows() >= 1){
		return $s1;
	}
		
	#check evidence 2: struct with substruct: blades <b>with</b> [bm?] margin
	#e.g. <n>phyllaries</n> <b>many</b> <b>in</b>  <b>4 � 6 </b> series , unequal , <m>outer</m> and <m>mid </m><b>with</b> appressed <n>bases</n> and <b>spreading</b> , <b>lanceolate</b> to <b>ovate</b> , spiny_fringed , <n>terminal</n> appendages , <b>at</b> least <m>mid </m>spine_tipped , <m><b>innermost</b></m> <b>with</b> <b>erect</b> , <b>flat</b> , <b>entire</b> , <b>spineless</b> <n>apices</n> .
	#<n>pappi</n> <b>fuscous</b> to <b>purplish</b> , <m>outer</m> scales  <b>25 � 30 , <</b> b> <b>0</</b> b> .  <b>2 � <</b> b> <b>1</</b> b> mm , contrasting <b>with</b>  <b>35 � 40 + , 5 � 7 + </b> mm <m>inner</m> <b>bristles</b> .
	#
	my $ptn = "(<[A-Z]>)*".$struct1."(</[A-Z]>)* <B>with</B> .* ?(<[A-Z]>)*".$struct2."(</[A-Z]>)*";
	$sth = $dbh->prepare("select sentence from ".$dataprefix."_sentence where  (tag != 'ignore' or isnull(tag)) and  sentence rlike '$ptn' ");
	$sth->execute() or  warn "$sth->errstr\n"; 
	while(($sentence) = $sth->fetchrow_array()){
		#$struct1 and 2 are grouped.
		if($sentence =~ /(?:<[A-Z]>)*\b$struct1r\b(?:<\/[A-Z]>)* <B>with<\/B> (.*) ?(?:<[A-Z]>)*\b$struct2r\b(?:<\/[A-Z]>)*/i){
			my $temp = $2;	
			$temp = removepairedbrackets($temp);
			if ($temp !~ /<N>/ && $temp !~ /[\(\[\{]/ && $temp !~ /\b($PROPOSITION)\b/){
				return $s2;
			}
		}
	}
	
	$ptn = "(<[A-Z]>)*$struct2(</[A-Z]>)* <b>with</b> .* ?(<[A-Z]>)*$struct1(</[A-Z]>)*";
	$sth = $dbh->prepare("select sentence from ".$dataprefix."_sentence where (tag != 'ignore' or isnull(tag)) and sentence rlike '$ptn' ");
	$sth->execute() or  warn "$sth->errstr\n"; 
	while(($sentence) = $sth->fetchrow_array()){
		if($sentence =~ /(?:<[A-Z]>)*\b$struct2r\b(?:<\/[A-Z]>)* <B>with<\/B> (.*) ?(?:<[A-Z]>)*\b$struct1r\b(?:<\/A-Z]>)*/i){
			my $temp = $2;
			$temp = removepairedbrackets($temp);
			if ($temp !~ /<N>/ && $temp !~ /[\(\)\[\]\{\}]/ && $temp !~ /\b($PROPOSITION)\b/){
				return $s1;
			}
		}
	}
	
	#evidence 3: clause-subclauses:  , <n> </n>
	#$ptn = "[,:;]</B> .* ? (<[A-Z]>)*$struct2(</[A-Z]>)*";
	$ptn = "[,:;]</B> (<[A-Z]>)*$struct2(</[A-Z]>)*";
	$sth = $dbh->prepare("select sentence from ".$dataprefix."_sentence where (tag != 'ignore' or isnull(tag)) and sentence rlike '$ptn' and tag rlike '$struct1'");
	$sth->execute() or  warn "$sth->errstr\n"; 
	while(($sentence) = $sth->fetchrow_array()){
		if($sentence =~ /[,:;]<\/B> (<[A-Z]>)*\b$struct2r\b(<\/[A-Z]>)*/i){
			#my $temp = $1;
			#$temp = removepairedbrackets($temp);
			#if ($temp !~ /<N>/ && $temp !~ /[\(\)\[\]\{\}]/){
				return $s2;
			#}	
		}
	}
	
	$ptn = "[,:;]</B> (<[A-Z]>)*$struct1(</[A-Z]>)*";
	$sth = $dbh->prepare("select sentence from ".$dataprefix."_sentence where (tag != 'ignore' or isnull(tag)) and sentence rlike '$ptn' and tag rlike '$struct2'");
	$sth->execute() or  warn "$sth->errstr\n"; 
	while(($sentence) = $sth->fetchrow_array()){
		if($sentence =~ /[,:;]<\/B> (<[A-Z]>)*\b$struct1r\b(<\/[A-Z]>)*/i){
			#my $temp = $1;
			#$temp = removepairedbrackets($temp);
			#if ($temp !~ /<N>/ && $temp !~ /[\(\)\[\]\{\}]/){
				return $s1;
			#}	
		}
	}
	#evidence 4: sentence-clauses 
	$sth = $dbh->prepare("select sentid from ".$dataprefix."_sentence where (tag != 'ignore' or isnull(tag)) and tag rlike '$struct2'");
	$sth->execute() or  warn "$sth->errstr\n"; 
	while(($sentid) = $sth->fetchrow_array()){
		my $parenttag = getparentsentencetag($sentid);
		if($parenttag =~ /$struct1r/){
			return $s2;
		}
	}
	
	$sth = $dbh->prepare("select sentid from ".$dataprefix."_sentence where (tag != 'ignore' or isnull(tag)) and tag rlike '$struct1'");
	$sth->execute() or  warn "$sth->errstr\n"; 
	while(($sentid) = $sth->fetchrow_array()){
		my $parenttag = getparentsentencetag($sentid);
		if($parenttag =~ /$struct2r/){
			return $s1;
		}
	}
return "";	
}

sub removepairedbrackets{
	my $sent = shift;
	my $temp;
	do{
		$temp = $sent;
		$sent =~ s#\(([^\(\)]*?)\)#\1#g; #remove paired brackets
		$sent =~ s#\[([^\[\]]*?)\]#\1#g;
		$sent =~ s#\{([^\{\}]*?)\}#\1#g;
	}while($temp ne $sent);
	return $sent;
}
############################################################################################
########################common substructure                #################################
############################################################################################

#sentences that are tagged with a commons substructure, such as blades, margins 
#need to be modified with its parent structure
sub commonsubstructure{
	my $common = collectcommonstructures();
	my ($sth, $sth1, $ptn, $sentid, $parentstructure, $modifier, $tag, $sentence);
	
	print "\n Common substructures: $common\n" if $debug;
	$ptn = "\\\\[?(".$common.")\\\\]?";
	$sth = $dbh->prepare("select sentid, modifier, tag, sentence from ".$dataprefix."_sentence where (tag != 'ignore' or isnull(tag)) and tag rlike '^$ptn\$'");
	$sth->execute() or warn "$sth->errstr\n";
	while(($sentid, $modifier, $tag, $sentence) = $sth->fetchrow_array()){
		if(!modifiercontainsstructure($modifier) && $tag !~/\[/){ 
			#when the common substructure is not already modified by a structure, and
			#when the tag is not already inferred from parent tag: mid/[phyllaries]
			$parentstructure = getparentsentencetag($sentid);

			my $ptag = $parentstructure;
			$parentstructure =~  s#([\[\]])##g;
			if($ptag ne "[parenttag]" && $modifier !~ /$parentstructure/ && $tag !~ /$parentstructure/ ){
				#remove any overlapped words btw $parentstructure and $tag
				$ptag =~ s#\b$tag\b##g;
				my $mcopy = $modifier;
				$modifier =~ s#(^\s*|\s*$)##g;
				$ptag =~ s#(^\s*|\s*$)##g;
				$ptag =~ s#\s+# #g;
				if (isatypemodifier($modifier)){
					$modifier = $modifier." ".$ptag;# cauline/base => cauline [leaf] / base
				}else{
					$modifier = $ptag." ".$modifier; #main marginal/spine => [leaf blade] main marginal/spine					
				}
				tagsentwmt($sentid, $sentence, $modifier, $tag, "commonsubstructure");
				#$sth1 = $dbh->prepare("update ".$dataprefix."_sentence set modifier = '$modifier' where sentid =$sentid ");
				#$sth1->execute() or warn "$sth1->errstr\n";
				#print "update ".$dataprefix."_sentence $sentid modifier ($mcopy, tag=$tag) with its parent tag $parentstructure to ($modifier)\n" if $debug;
			}
		}
	}
}


sub isatypemodifier{
	my $word = shift;
	my ($sth, @words, $w);
	@words = split(/\s+/, $word);
	$w = $words[@words-1];
	$sth = $dbh->prepare("select * from ".$dataprefix."_modifiers where word = '$w' and istypemodifier = 1");
	$sth->execute() or warn "$sth->errstr\n";
	while($sth->rows() > 0){
		return 1;
	}
	return 0;
}

sub modifiercontainsstructure{
	my $m = shift;
	my ($sth, $w, @w);
	@w = split(/\s+/, $m);
	foreach $w (@w){
		$w =~ s#\W##g; #remove []
		$sth = $dbh->prepare("select * from ".$dataprefix."_wordpos where pos in ('s', 'p') and word = '$w'");
		$sth->execute() or warn "$sth->errstr\n";
		if($sth->rows() > 0){
			return 1;
		}
	}
	return 0;
}

#find tags with more than one different structure modifiers
sub collectcommonstructures{
	my ($sth, $common, $tag, $modifier, $sth1, $count, $word, $allstructure);
	
	$sth = $dbh->prepare("select word from ".$dataprefix."_wordpos where pos in ('s','p') and word not in (select word from ".$dataprefix."_wordpos where pos ='b') ");
	$sth->execute() or warn "$sth->errstr\n";
	while(($word) = $sth->fetchrow_array()){
		$allstructure .= $word."|";
	}
	chop($allstructure);
	
	$sth = $dbh->prepare("select tag from ".$dataprefix."_sentence where (tag != 'ignore' or isnull(tag)) and tag not like '% %' and tag not like '%[%' group by tag having count( distinct modifier) > 1");
	$sth->execute() or warn "$sth->errstr\n";
	while(($tag) = $sth->fetchrow_array()){
		$sth1 = $dbh->prepare("select distinct modifier from ".$dataprefix."_sentence where tag = '$tag'");
		$sth1->execute() or warn "$sth->errstr\n";
		$count = 0;
		while(($modifier) = $sth1->fetchrow_array()){
			if($modifier =~/\b($allstructure)$/){
				$count++;
			}
		}
		if($count > 1){
			$common .= $tag."|";
		}
	}
	chop($common);
	$common =~ s#\|+#|#g;
	$common =~ s#\|+$##g;
	return $common;	
}

############################################################################################
######################## correct the markups for pronoun and character subjects  ###########
############################################################################################

#my $PRONOUN ="all|each|every|some|few|individual";
#my $CHARACTER ="lengths|length|width|widths|heights|height";
#my $PROPOSITION ="above|across|after|along|as|at|before|beneath|between|beyond|by|for|from|in|into|near|of|off|on|onto|over|than|throughout|toward|towards|up|upward|with|without";

sub pronouncharactersubject{
	my($sth, $pronptn, $charptn, $propptn, $sentid, $lead, $modifier, $tag, $sentence, $sth1, $sentcopy);
	#character cases
	#$tag = "ditto"; $modifier=""; #3/26/09
	my $t = "(?:<\/?[A-Z]>)?";
	my $t = "(?:<\/?[A-Z]+>)?"; #3/21/09
	$charptn = '('.$CHARACTER.')';
	$sth = $dbh->prepare("select sentid, lead, modifier, tag, sentence from ".$dataprefix."_sentence where (tag != 'ignore' or isnull(tag)) and lead rlike '(^| )$charptn( |\$)' or tag rlike '(^| )$charptn( |\$)' ");
	$sth->execute() or warn "$sth->errstr\n";
	while(($sentid, $lead, $modifier, $tag, $sentence) = $sth->fetchrow_array()){
		$sentcopy = $sentence;
		$sentence =~ s#></?##g; #3/21/09
		if($sentence =~/^.*?$t\b($CHARACTER)\b$t $t(?:of)$t (.*?)(<[NO]>([^<]*?)<\/[NO]> ?)+ /){#length of o
			$tag = $4;
			$modifier = substr($sentence, $-[2], ($-[4]-$-[2]));
			if($3 !~/\b($stop|\d)\b/ and $2 !~/\b($PROPOSITION)\b/){#5/01/09 added $2 condition
				$modifier =~ s#<\S+?>##g;
				$modifier =~ s#(^\s*|\s*$)##g;
				$tag =~ s#<\S+?>##g;
				$tag =~ s#(^\s*|\s*$)##g;
			}else{
				$tag = "ditto";
				$modifier = "";
			}
		#}elsif($sentence =~ /^(.*?)\s+$t($CHARACTER)$t/){#o length ; <M>ventral <N>profile; maximum width; subsemicircular profile
		}elsif($sentence =~ /^(.*?)((?:<\/?[BM]+>\w+?<\/?[BM]+>\s*)*)$t\b($CHARACTER)\b$t/){ #3/21/09
			my $text = $1; #text = maximum
			if($text !~/\b($stop|\d+)\b/ && $text=~/\w/ && $text !~/[,:;\.]/){
				$text =~ s#<\S+?>##g;
				$text =~ s#(^\s*|\s*$)##g;
				my @text = split(/\s+/, $text);
				$tag = $text[@text-1];
				#if(getnumber($tag) =~/[ps]/){
				if($sentence =~ /<[NO]>$tag<\/[NO]>/){ #5/01/09 check  9464, 9441, 2549, 2200, 2912
					$text =~ s#$tag$##g;
					$modifier = $text;
				}else{
					$tag = "ditto";
					$modifier = "";
				}
			}else{
				$tag = "ditto";
				$modifier = "";
			}
		}elsif($sentence=~/\b($CHARACTER)\b/){#lengths or $modifier contains a parenttag
			$tag = "ditto";
			$modifier = "";
		}
		tagsentwmt($sentid, $sentcopy, $modifier, $tag, "pronouncharactersubject[character subject]");
		
		#if($tag =~ /\b($CHARACTER)\b/){ #lengths, widths
		#	my @mcopy = split("/\s+/", $modifier);
		#	my $word = $mcopy[@mcopy-1];
		#	$word =~ s#[\[\]]##g;
		#	$sth1 = $dbh->prepare("select * from ".$dataprefix."_wordpos where word = '$word' and pos in ('s', 'p') ");
		#	$sth1->execute() or warn "$sth1->errstr\n";
		#	if($sth1->rows()>0){ #if $modifier is a O/N, find the good tag: o length / length of o
		#					}else{
				#length of o
		#		if($sentence =~/^.*?$t($CHARACTER)$t $t(?:of)$t (.*?)(<[NO]>([^<]*?)<\/[NO]> ?)+ /){#length of o
		#			$tag = $4;
		#			$modifier = substr($sentence, $-[2], ($-[4]-$-[2]));
		#			$modifier =~ s#<\S+?>##g;
		#			$modifier =~ s#(^\s*|\s*$)##g;
		#			$tag =~ s#<\S+?>##g;
		#			$tag =~ s#(^\s*|\s*$)##g;
		#		}else{
		#			tagsentwmt($sentid, $sentence, "", "ditto", "pronouncharactersubject[character subject]");
		#		}
		#	}
		#}else{#tag contains no CHARACTER, lead has
		#	
		#}
	}
	#propsition cases
	$propptn = '^('.$PROPOSITION.')';
	$sth = $dbh->prepare("select sentid, lead, modifier, tag, sentence from ".$dataprefix."_sentence where (tag != 'ignore' or isnull(tag)) and lead rlike '$propptn ' ");
	$sth->execute() or warn "$sth->errstr\n";
	while(($sentid, $lead, $modifier, $tag, $sentence) = $sth->fetchrow_array()){
		tagsentwmt($sentid, $sentence, "", "ditto", "pronouncharactersubject[proposition subject]");
	}
	
	#pronoun cases
	#5/01/09 pronoun could be in the middle of the modifier or tag "visceral areas of both valves" => visceral both valves areas???
	$pronptn = '('.$PRONOUN.')';
	$sth = $dbh->prepare("select sentid, lead, modifier, tag, sentence from ".$dataprefix."_sentence where tag rlike '(^| )$pronptn( |\$)' or modifier rlike '(^| )$pronptn( |\$)'");
	$sth->execute() or warn "$sth->errstr\n";
	while(($sentid, $lead, $modifier, $tag, $sentence) = $sth->fetchrow_array()){
		$modifier =~ s#\b($PRONOUN)\b##g;
		$tag =~ s#\b($PRONOUN)\b##g;
		$modifier =~ s#\s+# #g;
		$tag =~ s#\s+# #g;
		$tag = getparentsentencetag($sentid) if $tag !~/\w/ || $tag =~/ditto/;
		$modifier =~s#(^\s*|\s*$)##g;
		$tag =~s#(^\s*|\s*$)##g;
		my ($m,$tag) = getpartsfromparenttag($tag);
		$modifier .= " ".$m if $m =~/\w/;
		tagsentwmt($sentid, $sentence, $modifier, $tag, "pronouncharactersubject[pronoun subject]");
	}
	#$pronptn = '^('.$PRONOUN.')';
	#$sth = $dbh->prepare("select sentid, lead, modifier, tag, sentence from ".$dataprefix."_sentence where (tag != 'ignore' or isnull(tag)) and lead rlike '$pronptn '");
	#$sth->execute() or warn "$sth->errstr\n";
	#while(($sentid, $lead, $modifier, $tag, $sentence) = $sth->fetchrow_array()){
	#	if($modifier =~ /^\b($PRONOUN)\b(.*)$/){#remove pronoun from modifier
	#		$modifier = $2;
	#	}
	#	if($tag =~ /^\b($PRONOUN)\b(.*)$/){#remove pronoun from tag, if tag becomes empty, get parent tag
	#		$tag = $2;
	#	}
	#	$tag = getparentsentencetag($sentid) if $tag !~/\w/ || $tag =~/ditto/;
	#	$modifier =~s#(^\s*|\s*$)##g;
	#	$tag =~s#(^\s*|\s*$)##g;
	#	my ($m,$tag) = getpartsfromparenttag($tag);
	#	$modifier .= " ".$m if $m =~/\w/;
	#	tagsentwmt($sentid, $sentence, $modifier, $tag, "pronouncharactersubject[pronoun subject]");
	#}
	
	#errous noun cases : ligules surpassing phyllaries by 15 � 20 mm
	
	$sth = $dbh->prepare("select sentid, sentence, tag from ".$dataprefix."_sentence where (tag != 'ignore' or isnull(tag)) and tag not rlike ' (and|nor|or) ' and tag not like '%[%' and sentence collate utf8_bin not rlike concat('^[^N]*<N>',tag) ");
	$sth->execute() or warn "$sth->errstr\n";
	while(($sentid, $sentence, $tag) = $sth->fetchrow_array()){
		$sentcopy = $sentence;
		$sentence =~ s#></?##g; #3/21/09
		if($sentence =~ /^(\S*) ?<N>([^<]+)<\/N> <[MB]+>(\S+)<\/[MB]+> \S*\b$tag\b\S*/){ #correct to the N missed by doit
			$modifier = $1;
			$tag = $2;
			if($3 !~ /\bof\b/){
				$modifier =~ s#<\S+?>##g;
				$tag =~ s#<\S+?>##g;
				$modifier =~s#(^\s*|\s*$)##g;
		        $tag =~s#(^\s*|\s*$)##g;
				tagsentwmt($sentid, $sentcopy, $modifier, $tag, "pronouncharactersubject[correct to missed N]");
			}
		}
	}
}

############################################################################################
######################## tag remaining isnull(tag) sentences   #############################
############################################################################################
sub remainnulltag{
	my($sth, $sentid, $sentence);

	#NULL
	#if clause contains no N => ditto
	
	my $nphraseptn = "(?:<[A-Z]*[NO]+[A-Z]*>[^<]+?<\/[A-Z]*[NO]+[A-Z]*>\\s*)+";
	my $mphraseptn = "(?:<[A-Z]*M[A-Z]*>[^<]+?<\/[A-Z]*M[A-Z]*>\\s*)+";


	$sth = $dbh->prepare("select sentid, sentence from ".$dataprefix."_sentence where isnull(tag)");
	$sth->execute() or warn "$sth->errstr\n";
	while(($sentid, $sentence) = $sth->fetchrow_array()){
		#4/11/09
		 my $sentcopy = $sentence;
		 $sentcopy =~ s#></?##g;
		if($sentcopy !~/<[NO]>/){#contain a noun
			tagsentwmt($sentid, $sentence, "", "ditto", "remainnulltag-[R3]");
		}elsif($sentcopy =~ /(.*?)($nphraseptn)/){
			my $head = $1;
			my $tagphrase = $2;
			$tagphrase =~ s#(^\s+|\s+$)##g; #4/26/09 check 4633
			if ($head =~/\b($PROPOSITION)\b/){
				tagsentwmt($sentid, $sentence, "", "ditto", "remainnulltag-[R3:ditto]");
			}else{
				#tag = the last word in $tagphrase
				#modifier = anything in between , and the last word in $tagphrase
				my @words = split(/\s+/, $tagphrase);
        		my $tag = $words[@words-1]; #last word is the tag
        		splice(@words, @words-1);
        		my $modifier = join(" ", @words);
        		if($head =~ /([^,]+)$/){
        			$modifier = $1." ".$modifier;
        		}
        		$tag =~ s#<\S+?>##g;
        		$modifier =~ s#<\S+?>##g;
        		$tag =~ s#^\s+##;
        		$tag =~ s#\s+$##;
        		$modifier =~ s#^\s+##;
        		$modifier =~ s#\s+$##;
        		tagsentwmt($sentid, $sentence, $modifier, $tag, "remainnulltag-[R3:m-t]");
			}
		}
		
		#before 4/11/09
		#mark the remaining ditto: update words in the first segment as a b
		#if($sentence =~/(.*?)[,;:]/){
		#	my @first = split(/\s+/, $1);
		#	foreach (@first){
		# 		update($_, "b", "", "wordpos") if $_ !~/</;
		#	}
		#}
		#tagsentwmt($sentid, $sentence, "", "R3", "remainnulltag-[R3]");
	}
}


############################################################################################
######################## tag sentences                         #############################
############################################################################################
#pattern: one word sentence => boundary e.g. alternate; x x x pl cases
#3/26/09
sub markupbypos{
	my ($sth, $sth1, $sentid, $sentence, @words, $ptn, $sth1, $modifier, $tag, $sign, $boundary);
	
	print "::::::::::::::::::::::::::Markupbypos:\n" if $debug;
	do{
		$sign = 0;
		tagunknowns("singletag");
		#$sth = $dbh->prepare("select sentid, sentence from ".$dataprefix."_sentence where isnull(tag) or tag = 'ignore'");
		$sth = $dbh->prepare("select sentid, sentence from ".$dataprefix."_sentence where isnull(tag)"); #4/7/09
		$sth->execute() or warn "$sth->errstr\n";
		while(($sentid, $sentence) = $sth->fetchrow_array()){
			@words = split(/\s+/, $sentence);
			$ptn = sentenceptn("(################################)", @words+1, @words); #check all words
			if($ptn =~/^[qmb][,;:\.]$/){#one-word clause: alternate ; #3/24/09 q => qmb
				tagsentwmt($sentid, $sentence, "", "ditto", "remainnulltag-[R0]");
			#}elsif($ptn =~/^([mtq]*)([np]+)(,|;|:|.|b|(?<=p)q)/){
			}elsif($ptn =~/^([mtqb]*)([np]+)((?<=p)q)/ or $ptn =~/^([mtqb]*)([np]+)(,|;|:|\.|b)/){#3/21/09:5/23/09 split to 2 conditions:check 3708, 7673<M>cardinal</M> <N>process</N> shaft <M><B>weak</B></M>
				$boundary = $words[$-[3]]; #p/q at the end
				my @wordscopy = @words;
				$modifier = join(" ", splice(@wordscopy, 0, $+[1]));
				if($modifier !~/\b($PROPOSITION)\b/){ #condition added 3/21/09
					@wordscopy =@words;
					my @tag = splice(@wordscopy, $-[2], ($+[2] - $-[2]));
					$modifier .= " ".join(" ", splice(@tag, 0, @tag-1));
					$modifier =~ s#\s*$##g;
					$tag = $tag[@tag-1]; #[np]
					
					#update on q and p
					$sign += update($tag, "p", "-", "wordpos", 1) if $tag !~/</;
					my $mcopy = $modifier;
					while($mcopy =~/(?:^| )(\w+) (.*)/){ #nontagged words in modifier
						$sign += update($1, "m", "", "modifiers", 1); #update modifier
						$mcopy = $2;
					}
					$sign += update($boundary, "b", "", "wordpos", 1) if $boundary !~/</;
					$modifier =~ s#<\S+?>##g;
					$tag =~ s#<\S+?>##g;
					tagsentwmt($sentid, $sentence, $modifier, $tag, "remainnulltag-[R1]");
				}
			}elsif($ptn=~/^([^qpn,;:]*)([pn]+)[tmb]/){ #[bm]+[pn] #5/27/09 switch order btw R2 and R2.5 #5/28/09 add[tmb]
				my @wordscopy = @words;
				my $l = join(" ", splice(@wordscopy, 0, $+[1]));
				$boundary = $words[$+[2]] if $+[2] < @words;
				my @tag = splice(@words, $-[2], ($+[2]-$-[2]));
				if($l !~/\b($FORBIDDEN)/ && $l !~/\b($stop)\b/){
					if($l =~/.*?[,:;](.*)/){#5/30/09 add $l to $modifier
						$l = $1;
					}
					$modifier = $l." ".join(" ",splice(@tag, 0, @tag-1)); #5/23/09 add join
					$tag = $tag[0];
					#$sign += update($boundary, "b", "", "wordpos") if $boundary !~/</ and $boundary=~/\w/;
					$modifier =~ s#<\S+?>##g;
					$tag =~ s#<\S+?>##g;
					tagsentwmt($sentid, $sentence, $modifier, $tag, "remainnulltag-[R2]");
				}
			}		
			#}elsif($ptn=~/^([tm]*[sn]+q)([tmb])/){#5/23/09 cardinal process</N> shaft <B>often
			#	my @wordscopy = @words;
			#	$boundary = $words[$-[2]];
			#	my $newn = $words[$+[1]-1];#q is a n?
			#	my @tag = splice(@words, 0, $-[2]);
			#	my $isv = checkWN($newn, "pos");
			#	if(($boundary=~/\b(?:\w+ly)\b/ and $isv !~ /v/) or $isv !~/[var]/){ #$newn may be "originates"
			#	#if($isv !~/[var]/ ){
			#		$sign += update($newn, "n", "", "wordpos");
			#		$modifier = join(" ",splice(@tag, 0, @tag-1));
			#		$modifier =~ s#<\S+?>##g;
			#		$newn =~ s#<\S+?>##g;
			#		tagsentwmt($sentid, $sentence, $modifier, $newn, "remainnulltag-[R2.5]");
			#	}else{
			#		$sign += update($newn, "b", "", "wordpos");
			#	@tag = splice(@tag, 0, @tag-1); #remove the last word in @tag which is a v
			#		$modifier = join(" ",splice(@tag, 0, @tag-1));
			#		$modifier =~ s#<\S+?>##g;
			#		$tag[@tag-1] =~ s#<\S+?>##g;
			#		tagsentwmt($sentid, $sentence, $modifier, $tag[@tag-1], "remainnulltag-[R2.55]");
			#	}
				
				
		}
	}while($sign > 0);
	
	
}

sub tagsentwmt{
	my ($sentid, $sentence, $modifier, $tag, $label) = @_;
	my ($sth1);
	$modifier =~ s#<\S+?>##g;
	$tag =~ s#<\S+?>##g;

	#remove stop and forbidden words from beginning
	while($modifier =~ /^($stop|$FORBIDDEN)\b/){
		$modifier =~ s#^($stop|$FORBIDDEN)\b\s*##g;
	}
	
	while($tag =~ /^($stop|$FORBIDDEN)\b/){
		$tag =~ s#^($stop|$FORBIDDEN)\b\s*##g;
	}
	
	#from ending
	while($modifier =~ /\b($stop|$FORBIDDEN|\w+ly)$/){
		$modifier =~ s#\s*\b($stop|$FORBIDDEN|\w+ly)$##g;
	}
	
	while($tag =~ /\b($stop|$FORBIDDEN|\w+ly)$/){
		$tag =~ s#\s*\b($stop|$FORBIDDEN|\w+ly)$##g;
	}
	
	$modifier =~ s#\b($PRONOUN)\b##g; #5/11/09 check 4974, 7269
	
	#whether or not keep -ly words
	while($modifier =~/^(\w+ly)\s*(.*)/){
		my $ly = $1;
		my $rest = $2;
		$sth1 = $dbh->prepare("select * from ".$dataprefix."_wordpos where word = '$ly' and pos='b' ");
		$sth1->execute() or warn "$sth1->errstr\n";
		if($sth1->rows() > 0){
			$modifier = $rest;
		}else{
			last;
		}
	}
	
	$modifier=~ s#\s+# #g;
	$tag=~ s#\s+# #g;
	
	$modifier =~ s#(^\s*|\s*$)##g;
	$tag =~ s#(^\s*|\s*$)##g;
	if($tag eq "NULL"){
		$sth1 = $dbh->prepare("update ".$dataprefix."_sentence set tag = NULL, modifier = '$modifier' where sentid = $sentid");
	}else{
		if(length($tag) > $taglength){
			$tag = substr($tag, 0, $taglength);
			print "\n tag <$tag> longer than $taglength\n";
		}
		$sth1 = $dbh->prepare("update ".$dataprefix."_sentence set tag = '$tag', modifier = '$modifier' where sentid = $sentid");
	}
	$sth1->execute() or warn "$label: sentid $sentid:<$tag> $sth1->errstr\n";
	print "\n in $label: use modifier [$modifier], tag <$tag> to tag sentence $sentid: $sentence \n" if $debug and $label ne "normalizetags";
}
############################################################################################
########################markupbypattern                    #################################
############################################################################################

#2 n = or x = =>tag as chromosome
sub markupbypattern{
	my ($sth);
	$sth = $dbh->prepare("update ".$dataprefix."_sentence set tag = 'chromosome', modifier='' where originalsent like 'x=%' or originalsent like '2n=%' or originalsent like 'x %' or originalsent like '2n %' or originalsent like '2 n%'");
	$sth->execute() or warn "$sth->errstr\n";

	#foc
	$sth = $dbh->prepare("update ".$dataprefix."_sentence set tag = 'flowerTime', modifier='' where originalsent like 'fl.%'");
	$sth->execute() or warn "$sth->errstr\n";

	$sth = $dbh->prepare("update ".$dataprefix."_sentence set tag = 'fruitTime', modifier='' where originalsent like 'fr.%'");
	$sth->execute() or warn "$sth->errstr\n";


}

############################################################################################
########################markupignore, similar to , differ etc. #############################
############################################################################################

sub markupignore{
	my ($sth);
	#4/26/09	 
	#$sth = $dbh->prepare("update ".$dataprefix."_sentence set tag = 'ignore', modifier='' where originalsent rlike '^$IGNOREPTN ' or originalsent rlike '[^,;.]+ $IGNOREPTN '");
	$sth = $dbh->prepare("update ".$dataprefix."_sentence set tag = 'ignore', modifier='' where originalsent rlike '(^| )$IGNOREPTN ' ");
	$sth->execute() or warn "$sth->errstr\n";
	
	
}

sub finalizeignored{
	my ($sth, $sth1, $sentid, $sentence);
	
	$sth = $dbh->prepare("select sentid, sentence from ".$dataprefix."_sentence where tag = 'ignore'");
	$sth->execute() or warn "$sth->errstr\n";
	
	while(($sentid, $sentence) = $sth->fetchrow_array()){
		if($sentence =~ /(.*?)$IGNOREPTN/){
			if($1=~/<N>/){ #reset tag to null to be processed by remainningnull
				$sth1 = $dbh->prepare("update ".$dataprefix."_sentence set tag =NULL where sentid = $sentid");
				$sth1->execute() or warn "$sth1->errstr\n";
			}
		}
	}
	markupbypos();
}

sub untagsentences{
	my($sth, $sth1, $sentid, $sent);
 	$sth = $dbh->prepare("select sentid, sentence from ".$dataprefix."_sentence where (tag != 'ignore' or isnull(tag)) and sentence like '%<%'");
	$sth->execute() or warn "$sth->errstr\n";
	while(($sentid, $sent) = $sth->fetchrow_array()){
		$sent =~ s#<\S+?>##g; #remove any existing tag
		$sent =~ s#'#\\'#g;
		$sth1 = $dbh->prepare("update ".$dataprefix."_sentence set sentence ='$sent' where sentid =".$sentid);
		$sth1->execute() or warn "$sth1->errstr\n";
	}
 }

#mode: singletag or multitags
#singletag: n, m, b, 
#multitags: o, n, m, b
sub knowntags{
	my $mode = shift;
	my ($sth, $sth1, $sent, $sentid, $n, $m, $b, $b1, $o,$z, $word);
	#gather nouns from ".$dataprefix."_wordpos
	$sth = $dbh->prepare("select word from ".$dataprefix."_wordpos where pos ='p' or pos = 's'");
	$sth->execute() or warn "$sth->errstr\n";
	while(($word) = $sth->fetchrow_array()){
		$n .= $word."|" if length($word)>0;
	}
	if($mode eq "singletag"){
		#additional nouns from tags
		$sth = $dbh->prepare("select distinct tag from ".$dataprefix."_sentence where (tag != 'ignore' or isnull(tag)) and tag not like '% %' and tag not like '%[%' and tag not in (select word from ".$dataprefix."_wordpos where pos ='p' or pos = 's')");
		$sth->execute() or warn "$sth->errstr\n";
		while(($word) = $sth->fetchrow_array()){
			$n .= $word."|";
		}
	}
	chop($n);
	
	if($mode ne "singletag"){
		#Hong: Dec 9, try separate o tag
		$sth = $dbh->prepare("select distinct tag from ".$dataprefix."_sentence where (tag != 'ignore' or isnull(tag)) and tag not like '% %' and tag not like '%[%'");
		$sth->execute() or warn "$sth->errstr\n";
		while(($word) = $sth->fetchrow_array()){
			$word =~ s#[\[\]]##g;
			$o .= $word."|" if length($word)>0;
		}
		chop($o);
	}
	
	#gather non-tag modifiers
	#middle/middles case: middle is a "s" and "m" at the same time. Should allow words such as middle to have two roles
	#get all modifiers, hong 12/9
	if($mode eq "singletag"){
		$sth = $dbh->prepare("select word from ".$dataprefix."_modifiers where word not in (select distinct word from ".$dataprefix."_wordpos where pos ='s' or pos='p')");
	}else{
		$sth = $dbh->prepare("select distinct word from ".$dataprefix."_modifiers");
	}
	$sth->execute() or warn "$sth->errstr\n";
	while(($word) = $sth->fetchrow_array()){
		$m .= $word."|" if length($word)>0;
	}
	chop($m);
	
	#gather boundary words from ".$dataprefix."_wordpos
	$sth = $dbh->prepare("select word from ".$dataprefix."_wordpos where pos ='b'");
	$sth->execute() or warn "$sth->errstr\n";
	while(($word) = $sth->fetchrow_array()){
		if($word =~/^[-\(\)\[\]\{\}\.\|\+\*\?]$/){
			$b1 .= "\\".$word."|"; #b1 includes punct marks, not need for \b when matching
		}elsif($word !~/\w/ && $word ne "/"){
			$b1 .= $word."|" if length($word)>0;;
		}else{
			$b .= $word."|" if length($word)>0;
		}
	}
	chop($b);
	chop($b1);
	
	#6/02/09
	#gather proper nouns from ".$dataprefix."_wordpos
	$sth = $dbh->prepare("select word from ".$dataprefix."_wordpos where pos ='z'");
	$sth->execute() or warn "$sth->errstr\n";
	while(($word) = $sth->fetchrow_array()){
			$z .= $word."|" if length($word)>1;
	}
	chop($z);
	
	return ($n, $o, $m, $b, $b1, $z);
}

############################################################################
############   fix "x with y"           ####################################
############################################################################

sub fixxwithy{
	my ($sth, $sth1, $sentid, $tag, $lead, $firstn, $secondn, $sentence);
	$sth = $dbh->prepare("select sentid, tag, lead, sentence from ".$dataprefix."_sentence where (tag != 'ignore' or isnull(tag)) and lead like '% with %'"); 
	$sth->execute() or warn "$sth->errstr\n";
	while(($sentid, $tag, $lead, $sentence) = $sth->fetchrow_array()){
		if($lead =~/^(\w+)\s+with\s+(\w+)/){
			$firstn = $1;
			#$secondn = $2; 
			tagsentwmt($sentid, $sentence, "", $firstn, "In fixwwithy");
		}
	}
}

#annotated unknown sentences with the terms learned
#tags: n-sg/pl, b-bdry, m-modifier
#many flat teeth => <b>many</b> flat <p>teeth</p> 
#mode: singletag or multitags
sub tagunknowns{
	my $mode = shift;
	my ($n,$o, $m, $b, $b1, $z) = knowntags($mode); #depending on the mode, $o could be empty
	my ($sth, $sentid, $sent, $sth1);
	$sth = $dbh->prepare("select sentid, sentence from ".$dataprefix."_sentence where isnull(tag) and lead not like 'similar to %'");
	$sth->execute() or warn "$sth->errstr\n";
	while(($sentid, $sent) = $sth->fetchrow_array()){
		$sent =~ s#<\S+?>##g; #remove any existing tag
		$sent = annotateSent($sent, $n, $o, $m, $b, $b1, $z);
		$sth1 = $dbh->prepare("update ".$dataprefix."_sentence set sentence ='$sent' where sentid =".$sentid);
		$sth1->execute() or warn "$sth1->errstr\n";
	}
}

#mode: singletag or multitags
#multitags tag words with all o n m b tags that are applicable to the words.
sub tagallsentences{
	my ($mode, $type) = @_;
	my ($n, $o, $m, $b, $b1, $z) = knowntags($mode); #depending on the mode, $o could be empty #6/02/09 add $z
	my ($sth, $sentid, $sent, $sth1);
	if($type eq "original"){
		$sth = $dbh->prepare("select sentid, originalsent from ".$dataprefix."_sentence");#tag all including "ignore"
	}else{
		$sth = $dbh->prepare("select sentid, sentence from ".$dataprefix."_sentence");
	}
	$sth->execute() or warn "$sth->errstr\n";
	while(($sentid, $sent) = $sth->fetchrow_array()){
		$sent =~ s#<\S+?>##g; #remove any existing tag
		$sent =~ tr/A-Z/a-z/;
		$sent =~ s#\s*-\s*([a-z])#_\1#g;                   #cup_shaped, 3_nerved, 3-5 (-7)_nerved 
		$sent =~ s#(\W)# \1 #g;                            #add space around nonword char 
    	$sent =~ s#\s+# #g;                                #multiple spaces => 1 space 
    	$sent =~ s#^\s*##;                                 #trim
    	$sent =~ s#\s*$##; 
		$sent = annotateSent($sent, $n, $o, $m, $b, $b1, $z);
		#$sent =~ s#'#\\'#g;
		$sth1 = $dbh->prepare("update ".$dataprefix."_sentence set sentence ='$sent' where sentid =".$sentid);
		$sth1->execute() or warn "$sth1->errstr\n";
	}
	
	#my ($sth, $sentid, $originalsent, $sth1);
	#$sth = $dbh->prepare("select sentid, originalsent from ".$dataprefix."_sentence where (tag != 'ignore' or isnull(tag)) ");
	#$sth->execute() or warn "$sth->errstr\n";
	#while(($sentid, $originalsent) = $sth->fetchrow_array()){
	#	$originalsent =~ s#<\S+?>##g; #remove any existing tag
	#	$originalsent =~ tr/A-Z/a-z/;
	#	$originalsent =~ s#\s*-\s*([a-z])#_\1#g;                   #cup_shaped, 3_nerved, 3-5 (-7)_nerved 
	#	$originalsent =~ s#(\W)# \1 #g;                            #add space around nonword char 
    #	$originalsent =~ s#\s+# #g;                                #multiple spaces => 1 space 
    #	$originalsent =~ s#^\s*##;                                 #trim
    #	$originalsent =~ s#\s*$##; 
	#	$originalsent = annotateSent($originalsent, $n, $o, $m, $b, $b1);
	#	$sth1 = $dbh->prepare("update ".$dataprefix."_sentence set sentence =\"".$originalsent."\" where sentid =".$sentid);
	#	$sth1->execute() or warn "$sth1->errstr\n";
	#}
}

#depending on what is in n, o, m b, a word may or may not be tagged with multiple tags
sub annotateSent{
	my ($sent, $n, $o, $m, $b, $b1, $z) = @_;
	#$b contains boundarywords, stopwords, numbers, and brackets.
	$b =~ s#\b(_[a-z]+)\b#(?\:\\b\\d+)\1#g; #_nerved => (?:\b\d+)_nerved
	$n =~ s#\b($NONS)\b##g; #4/20/09 "laterals" becomes lateral in tag, but lateral is a mb, not a n.
	$o =~ s#\b($NONS)\b##g; #4/20/09
	$n =~ s#\|+#|#g; #4/20/09
	$o =~ s#\|+#|#g; #4/20/09
	
	$sent =~ s#\b($z)\b#<Z>\1</Z>#g if $z =~ /\w/; #6/02/09
	$sent =~ s#\b($o)\b#<O>\1</O>#g if $o =~ /\w/;
	$sent =~ s#\b($n)\b#<N>\1</N>#g if $n =~ /\w/;
	$sent =~ s#\b($m)\b#<M>\1</M>#g if $m =~ /\w/; #order matters, first tag <m>
	$sent =~ s#\b($b)\b#<B>\1</B>#g if $b =~ /\w/; #then <b> so when double tagged <m><b>basal</b></m>
	$sent =~ s#($b1)#<B>\1</B>#g if length $b1 > 0 ; #then <b> so when double tagged <m><b>basal</b></m>
	#$sent =~ s#([\(\)\[\]\{\}])#<b>\1</b>#gi; #tag parentheses <b>
	#$sent =~ s#((?:\(? ?\d+\W*)+)# <b>\1</b> #gi; # tag all numbers <b>. Not here. Need to tag tokens one by one

	$sent =~ s#<(\w)>\s*</\1>##g; #remove <></> and <> </>
	$sent =~ s#(?:<[^<]+>)+($FORBIDDEN)(?:</[^<]+>)+#\1#g;
	return $sent; 
}


sub separatemodifiertag{	
  my ($sth,$sth1, $tag, $ntag, $sth1, $sentid, @words, $modifier, $i, $j, $tmp, $sentence);
  $sth = $dbh->prepare("select sentid, tag, sentence from ".$dataprefix."_sentence where tag like '% %'"); #4/29/09
  #$sth = $dbh->prepare("select sentid, tag, sentence from ".$dataprefix."_sentence where (tag != 'ignore' or isnull(tag)) and !isnull(tag)");
  $sth->execute() or warn "$sth->errstr\n";
  while(($sentid, $tag, $sentence) = $sth->fetchrow_array() ){
      $ntag = $tag;
      if( $ntag =~/\w+/){
       	if($ntag !~ /\b($NounHeuristics::STOP)\b/){
        	@words = split(/\s+/, $ntag);
        	$tag = $words[@words-1]; #last word is the tag
        	splice(@words, @words-1);
        	$modifier = join(" ", @words);
        	if(!defined ($modifier)) {$modifier = "";}
        	if($tag =~/\w/){
        		tagsentwmt($sentid, $sentence, $modifier, $tag, "separatemodifiertag");
       		}else{
       			tagsentwmt($sentid, $sentence, $modifier, 'NULL', "separatemodifiertag");
       		}
       }else{
       		 # treat them case by case
        	#case 1: in some species, abaxially with =>NULL
        	if($ntag =~/^in / || $ntag =~/\b(with|without)\b/){
        		tagsentwmt($sentid, $sentence, "", 'NULL', "separtemodifiertag");
        	}else{
          		#case 2: at least some leaves/all filaments/all leaves/more often shrubs/some ultimate segements
          		my $t = $ntag;
          		$ntag =~ s#\b($NounHeuristics::STOP)\b#@#g;
          		if($ntag =~ /@ ([^@]+)$/){
            		my $tg = $1;
            		my @tg = split(/\s+/, $tg);
            		$tag = $tg[@tg-1];
            		splice(@tg, @tg-1);
            		$modifier = join(" ", @tg);
            		if(!defined ($modifier)) {$modifier = "";}
            		if($tag =~/\w/){
            			tagsentwmt($sentid, $sentence, $modifier, $tag, "separatemodifiertag");
             		}else{
            			tagsentwmt($sentid, $sentence, "", "NULL", "separatemodifiertag");
            		}
          		}#if
        	}#else
       }
      }#if
  }#while
 }
 
##############################################################################
###########  normalize tags                      ########################
##############################################################################
#turn all tags and modifiers to singular form

#reset "of" tags to NULL, for example "aspects of snout".
 
sub normalizetags{
	my ($sth, $sth1, $sentid, $tag, $modifier, $bracted, $sentence);
	$sth = $dbh->prepare("select sentid, sentence, tag, modifier from ".$dataprefix."_sentence where (tag != 'ignore' or isnull(tag))");
	$sth->execute() or warn "$sth->errstr\n";
 	while(($sentid, $sentence, $tag, $modifier) = $sth->fetchrow_array()){
 		if($sentence =~/$tag\s+of\s+/){
			tagsentwmt($sentid, $sentence, "", "NULL", "normalizetags"); 			
 		}else{
 			$tag = normalizethis($tag);
 			$modifier = normalizethis($modifier);
 			if($tag =~/\w/){
 				tagsentwmt($sentid, $sentence, $modifier, $tag, "normalizetags");
 			}else{
 				tagsentwmt($sentid, $sentence, $modifier, "NULL", "normalizetags");
 			}
 		}
 	}
} 

sub normalizethis{
	my $tag = shift;
	$tag =~ s#\s*NUM\s*# #g;
	$tag =~ s#(^\s*|\s*$)##g;
	if($tag =~/\w/){
 			$tag =~ s#\[#[*#g;
 			$tag =~ s#\]#*]#g;
 			my @twsegs = split(/[\]\[]/, $tag);
 			$tag ="";
 			for(my $j = 0; $j < @twsegs; $j++){
 				my $out ="";
				if($twsegs[$j] =~/\*/){ #parts in []
					$twsegs[$j] =~ s#\*##g;
					my @tagwords = split(/\s+/, $twsegs[$j]);
					$out ="[";
					for(my $i = 0; $i < @tagwords; $i++){
						$tagwords[$i] = singular($tagwords[$i]);
						$out .=$tagwords[$i]." ";
					}
					chop($out);
					$out .="]";
				}elsif($twsegs[$j]=~/\w/){
					my @tagwords = split(/\s+/, $twsegs[$j]);
					for(my $i = 0; $i < @tagwords; $i++){
						$tagwords[$i] = singular($tagwords[$i]);
						$out .=$tagwords[$i]." ";
					}
					chop($out);
				}
				
				$tag .=$out." " if $out=~/\w/;
 			}
 			chop($tag);
 			$tag =~ s#\s+# #g;
 		}
 		return $tag;
}

##############################################################################
###########  normalize modifiers                      ########################
##############################################################################
#remove <b> from modifiers

my %checkedmodifiers = ();
sub normalizemodifiers{
	my ($sth, $sth1, $sentid, $tag, $modifier, $bracted, $sentence, $mcopy);
	#non- and/or/to/plus cases
	$sth = $dbh->prepare("select sentid, sentence, tag, modifier from ".$dataprefix."_sentence where modifier!=''  and modifier not rlike ' (and|or|nor|plus|to) ' order by length(modifier) desc");
	$sth->execute() or warn "$sth->errstr\n";
	
 	while(($sentid, $sentence, $tag, $modifier) = $sth->fetchrow_array()){
 		$mcopy = $modifier;
 		print "\n" if $debug;
 		$modifier = finalizemodifier($modifier, $tag, $sentence);
 		$modifier =~ s#\s*\[.*?\]\s*# #g;
 		$modifier =~ s#^\s*##;
 		$modifier =~ s#\s*$##;
 		if($mcopy ne $modifier){
 			print "sent [$sentid] modifier [$mcopy] is changed to [$modifier]\n" if $debug;
 			tagsentwmt($sentid, $sentence, $modifier, $tag, "normalizemodifiers");
 		}
 	}
 	
 	#deal with to: characterA to characterB organ (small to median shells)
 	$sth = $dbh->prepare("select sentid, sentence, tag, modifier from ".$dataprefix."_sentence where modifier rlike ' to ' order by length(modifier)");
	$sth->execute() or warn "$sth->errstr\n";
 	while(($sentid, $sentence, $tag, $modifier) = $sth->fetchrow_array()){
 		$mcopy = $modifier;
 		$modifier =~ s#.*? to ##;
 		my @mwords = split(/\s+/, $modifier);
 		@mwords = reverse(@mwords);
 		
 		my $m = "";
 		$sth1 = $dbh->prepare("select count(*) from ".$dataprefix."_sentence where modifier ='$m' and tag ='$tag'");
 		$sth1->execute() or warn "$sth1->errstr\n";
 		my ($count) = $sth1->fetchrow_array();
 		my $modi = $m;
 		foreach (@mwords){
 			$m = $_." ".$m;
 			$m =~ s#\s+$##;
 			$sth1 = $dbh->prepare("select count(*) from ".$dataprefix."_sentence where modifier ='$m' and tag ='$tag'");
 			$sth1->execute() or warn "$sth1->errstr\n";
 			my ($c) = $sth1->fetchrow_array();
 			if($c > $count){
 				$count = $c;
 				$modi = $m;
 			}
		}
		print "\n[nomalize to-cases]: modifier [$mcopy] is changed to [$modi]\n" if $debug;
 		tagsentwmt($sentid, $sentence, $modi, $tag, "normalizemodifiers");
 	}

	#4/26/09
	#modifier with and/or/plus
	$sth = $dbh->prepare("select sentid, sentence, tag, modifier from ".$dataprefix."_sentence where modifier rlike ' (and|or|nor|plus|to) ' order by length(modifier) desc");
	$sth->execute() or warn "$sth->errstr\n";
	
 	while(($sentid, $sentence, $tag, $modifier) = $sth->fetchrow_array()){
 		$mcopy = $modifier;
 		print "\n" if $debug;
 		$modifier = finalizecompoundmodifier($modifier, $tag, $sentence);
 		$modifier =~ s#\s*\[.*?\]\s*# #g;
 		$modifier =~ s#^\s*##;
 		$modifier =~ s#\s*$##;
 		if($mcopy ne $modifier){
 			print "sent [$sentid] modifier [$mcopy] is changed to [$modifier]\n" if $debug;
 			tagsentwmt($sentid, $sentence, $modifier, $tag, "normalizemodifiers");
 		}
 	}
 	
 		#4/29/09
	#modifier with and/or/plus
	$sth = $dbh->prepare("select sentid, sentence, tag, modifier from ".$dataprefix."_sentence where tag rlike ' (and|or|nor|plus|to) ' order by length(tag) desc");
	$sth->execute() or warn "$sth->errstr\n";
	
 	while(($sentid, $sentence, $tag, $modifier) = $sth->fetchrow_array()){
 		my $mtag = $tag;
 		print "\n" if $debug;
 		$tag = finalizecompoundtag($tag, $sentence);
 		$tag =~ s#\s*\[.*?\]\s*# #g;
 		$tag =~ s#^\s*##;
 		$tag =~ s#\s*$##;
 		if($mtag ne $tag){
 			print "sent [$sentid] modifier [$mtag] is changed to [$tag]\n" if $debug;
 			tagsentwmt($sentid, $sentence, $modifier, $tag, "normalizemodifiers");
 		}
 	}
 		
 	if($debug){
 		print "In normalizemodifers [summary]: \n";
 		foreach (keys(%checkedmodifiers)){
 			print "$_ : $checkedmodifiers{$_}\n";
 		}
 	}
}

#4/29/09
#[bm]+n+&[bm]+n+
sub finalizecompoundtag{
	my ($tag, $sentence) = @_;
	my $tcopy = $tag;
	my $result = "";
	#components
	my @parts = ();
	my @conj = ();
	push(@conj, "");

	while($tag =~ /(^.*?) (and|or|nor|plus) (.*)/){
		push(@parts, $1);
		push(@conj, $2);
		$tag = $3;
	}
	push(@parts, $tag);
	#at least one m in a part
	for (my $i =0; $i < @parts; $i++){
		my @words = split(/\s+/, $parts[$i]);
		my $foundam = 0;
		my $r = "";
		foreach my $w (@words){
			if($checkedmodifiers{$w} == 1 or $sentence =~ /<N>$w/){
				$foundam = 1;
				$r = $r." ".$w;
			}
		}
		$r =~s#\b($CHARACTER|$stop|$NUMBERS|$CLUSTERSTRINGS)\b/##g;
		$r =~ s#(^\s+|\s$)##g;
		$result = $result." ".$conj[$i]." ".$r if $r =~/\w/;
	}
	$result =~ s#\s+# #g;	
	$result =~ s#(^\s+|\s+$)##g;
 	print "finalizecompoundtag: $tcopy => $result\n" if $debug;
	return $result;
}
#4/26/09
#b&bm[n]
#m&m[n]

sub finalizecompoundmodifier{
	my ($modifier, $tag, $sentence) = @_;
	return $modifier if $modifier =~/\[/;
	my $mcopy = $modifier;
	my $result = "";
	#get the last m/n out
	my $m = "";
	my $n = "";
	my @lastpart = reverse(split(/\s+/, $modifier));
	my $cut = 0;
	foreach my $l (@lastpart){
		if($cut==0 and $sentence =~ /<N>$l/){
			$n = $l." ".$n;
			$n =~ s#(^\s+|\s$)##g;
		}else{
			$cut = 1;
			my $tm = $n=~/\w/? $l." ".$n : $l; #5/09/09 check sentid 5380 elevated and elongate dorsal muscle/scars
			my $sth = $dbh->prepare("select * from ".$dataprefix."_sentence where modifier ='$tm' and tag ='$tag' ");
			$sth->execute() or warn "$sth->errstr\n";
			if($sth->rows()>0){
				$m = $l." ".$m;
			}else{
				last;
			}
		}
	}
	$m =~ s#(^\s+|\s$)##g;	
	$n =~ s#(^\s+|\s$)##g;	
	$modifier =~ s#\s*$n##;
	

	#components
	my @parts = ();
	my @conj = ();
	push(@conj, "");

	while($modifier =~ /(^.*?) (and|or|nor|plus) (.*)/){
		push(@parts, $1);
		push(@conj, $2);
		$modifier = $3;
	}
	push(@parts, $modifier);
	#at least one m in a part
	for (my $i =0; $i < @parts; $i++){
		my @words = split(/\s+/, $parts[$i]);
		my $foundam = 0;
		my $r = "";
		foreach my $w (@words){
			if($checkedmodifiers{$w} == 1 or $sentence =~ /<N>$w/){
				$foundam = 1;
				$r = $r." ".$w;
			}
		}
		$r =~ s#(^\s+|\s$)##g;

		#if ($foundam == 0){
		#	$r = $words[@words-1];
		#}
		$result = $result." ".$conj[$i]." ".$r;
		if($r !~/\w/ or $r =~/\b($CHARACTER|$stop|$NUMBERS|$CLUSTERSTRINGS)\b/){
			$result = "";
			last;
		}
	} 
	$result = $result=~/\w/? $result." ".$n : $m." ".$n;
	$result =~ s#(^\s+|\s+$)##g;
 
	print "finalizecompoundmodifier: $mcopy => $result\n" if $debug;
	return $result;
}

sub finalizemodifier{
	my ($modifier, $tag, $sentence) = @_;
	my $fmodifier ="";
	$modifier =~ s#\[.*?\]##g; #remove [sdf] in the modifier
	$modifier =~ s#(^\s+|\s+$)##g;
	if($modifier=~/\w/){
		my @mwords = split(/\s+/, $modifier);
		my @rmwords = reverse(@mwords);
	
		foreach (@rmwords){ #take up to the last modifier
			my $ism = ism($_, $modifier, $tag);
			if($ism==1){
				$fmodifier = $_." ".$fmodifier;
			}else{
				last; #make the remaining -1?
			}
		}
		$fmodifier =~ s#\s+$##g;
	}
	return $fmodifier;
}

sub ism{
	my ($word, $modifier, $tag) = @_;
	my ($sth, $sth1);
	#return -1 if $word =~/_/; #4/29/09
	#if($word =~/\[/ or $word =~/\]/){return 1;}
	#if $word and $tag is separated by a comma, return 0
	#will never get into this condition
	#if($modifier =~/(.*?),[^,]+$/ and $1=~/\b$word\b/){
	#	#$checkedmodifiers{$word} .= '0[$tag]';
	#	$checkedmodifiers{$word} = -1;
	#	print "$word: [-1-comma]" if $debug;
	#	return -1;
	#}
	#check $checkedmodifiers
	#if($checkedmodifiers{$word}=~/1[$tag]/){
	if($checkedmodifiers{$word}==1){	
		print "$word: [1-checked]" if $debug;
		return 1;
	}elsif($checkedmodifiers{$word}==-1){
	#}elsif($checkedmodifiers{$word}=~/0[$tag]/){
		print "$word: [-1-checked]" if $debug;
		return -1;
	}
	#if $word is a "s", return 1
	$sth = $dbh->prepare("select * from ".$dataprefix."_wordpos where word = '$word' and pos in ('s','p', 'n')"); #5/1/09 add p, n in the list. because this is called before noramlizetags "areas of both valves"
	$sth->execute() or warn "$sth->errstr\n";
 	if($sth->rows>0){
 		#$checkedmodifiers{$word} .= '1[$tag]';
 		$checkedmodifiers{$word} = 1;
 		print "$word: [1-noun]" if $debug;
		return 1;
 	}
 	#if $word is a "b", and not a "m", return 0
 	$sth = $dbh->prepare("select * from ".$dataprefix."_wordpos where word = '$word' and pos = 'b'");
	$sth->execute() or warn "$sth->errstr\n";
	$sth1 = $dbh->prepare("select * from ".$dataprefix."_modifiers where word = '$word'");
	$sth1->execute() or warn "$sth1->errstr\n";
 	if($sth->rows>0 and $sth1->rows==0){ #only b
 		#$checkedmodifiers{$word} .= '0[$tag]';
 		$checkedmodifiers{$word} = -1;
 		print "$word: [-1-all-b]" if $debug;
		return -1;
 	}
 	if($sth1->rows>0 and $sth->rows==0){#only m
 		#$checkedmodifiers{$word} .= '1[$tag]';
 		$checkedmodifiers{$word} = 1;
 		print "$word: [1-all-m]" if $debug;
		return 1;
 	}
 	
 	#when $word has been used as "b" and "m" or neither "b" nor "m" and is not a "s"
 	my $mcount = getmcount($word);#4/29/09
	my $wcopy = $word; #5/01/09 $wcopy
	if($word =~/_/){
		$wcopy =~s#_# - #g;
	}
	$sth1 = $dbh->prepare("select count(*) from ".$dataprefix."_sentence where originalsent rlike '(^| )$wcopy '");
	$sth1->execute() or warn "$sth1->errstr\n";
	my ($tcount) = $sth1->fetchrow_array();
	
 	if($tcount ==0 or $tcount > 2.05 * $mcount){#4/22/09
 		#$checkedmodifiers{$word} .= '0[$tag]';
 		$checkedmodifiers{$word} = -1;
 		print "$word: [-1-total/m=$tcount/$mcount]" if $debug;
		return -1;
 	}else{
 		#$checkedmodifiers{$word} .= '1[$tag]';
 		$checkedmodifiers{$word} = 1;
 		print "$word: [1-total/m=$tcount/$mcount]" if $debug;
 		return 1;
 	}
 	print "$word: [-1-otherwise]" if $debug; 	
 	return -1;
}

#4/29/09
sub getmcount{
	my $word = shift;
	my ($sentence, $mcount, $sth);
	
	#my $ptn = "(>| )$word(</B></M>)? [^,]*<N";
 	#$sth = $dbh->prepare("select sentence from ".$dataprefix."_sentence where sentence COLLATE utf8_bin rlike  '$ptn'"); #4/29/09
	#$sth->execute() or warn "$sth->errstr\n";
	#while(($sentence) = $sth->fetchrow_array()){
	#	if($sentence =~ /(?:>| )$word(?:<\/B><\/M>)? ([^,]*?)<N/){
	#		if($1 !~/\b($PROPOSITION)\b/){
	#			$mcount++;
	#		}
	#	}
	#}
	
	my $ptn = "(>| )$word(</B></M>)? <N";
	$sth = $dbh->prepare("select sentence from ".$dataprefix."_sentence where sentence COLLATE utf8_bin rlike  '$ptn'"); #4/29/09
	$sth->execute() or warn "$sth->errstr\n";
	$mcount = $sth->rows();
	return $mcount;
}

#######################################
#test
sub listtruemodifiers{
	my ($sth, $sth1, $sth2, $tag, $source, $modifier, %m, %nm, %list);
	$sth = $dbh->prepare("select distinct tag from ".$dataprefix."_sentence where tag not like '% %' and not isnull(tag)");
	$sth->execute() or warn "$sth->errstr\n";
	while(my ($tag) = $sth->fetchrow_array()){
		%nm = ();
		%m =();
		%list = ();
		$sth1 = $dbh->prepare("select source, modifier from ".$dataprefix."_sentence where tag = '$tag' and modifier not like '% %' and modifier !=''");
		$sth1->execute() or warn "$sth1->errstr\n";
		while(my ($source, $modifier) = $sth1->fetchrow_array()){
			$source =~ s#-.*##g;
			if(isnoun($modifier)){
				$nm{$modifier}=1;
			}else{
				if($m{$source} !~ /\b$modifier\b/){
					$m{$source} .= " ".$modifier;
				}
			}
		}
		
		foreach my $s (keys(%m)){
			if($m{$s}=~/\w \w/){
				my @a = split(/\s+/, $m{$s});
				foreach (@a){
					if(/\w/){
						$list{$_} = 1;
					}
				}
			}
		}
		
		print "true modifiers for tag $tag:\n";
		print "nouns: ";
		foreach (keys(%nm)){
			print "$_;";
		}
		print "\nmodifiers:";
		foreach (keys(%list)){
			print "$_;";
		}
		print "\n\n";
	}
	
}

sub isnoun{
	my $word = shift;
	my $sth = $dbh->prepare("select word from ".$dataprefix."_wordpos where word ='$word' and pos in ('s','p')");
	$sth->execute() or warn "$sth->errstr\n";
	if($sth->rows() > 0){
		return 1;
	}
	
	$sth = $dbh->prepare("select * from ".$dataprefix."_sentence where tag ='$word'");
	$sth->execute() or warn "$sth->errstr\n";
	if($sth->rows() > 0){
		return 1;
	}
	return 0;
	
}
############################################################################### 
# turn a plural word to its singular form
sub singular{
 my $p = shift;
 #print "in: $p\n";
 return "" if $p!~/\w/;
 my $s; 
 if($p eq "valves"){ return "valve"};
 if($p eq "media"){ return "media"};
 if($p eq "frons"){return "frons"};
 if($p eq "species") {return "species"};
 if(getnumber($p) eq "p"){
    if($p =~ /(.*?[^aeiou])ies$/){
      $s = $1.'y';
    }elsif($p =~/(.*?)i$/){
      $s = $1.'us';
    }elsif($p =~/(.*?)ia$/){
      $s = $1.'ium';
    }elsif($p =~/(.*?(x|ch|sh|ss))es$/){#3/12/09 add ss for recesses and processes
      $s = $1;
    }elsif($p =~/(.*?)ves$/){
      $s = $1."f";
    }elsif($p =~ /(.*?)ices/){
    	$s = $1."ex";
    }elsif($p =~/(.*?a)e$/ || $p=~/(.*?)s$/ ){#pinnae ->pinna, fruits->fruit
      $s = $1;
    }
  }
  return $s if $s=~/\w/;
  my $singular = checkWN($p, "singular");
  #print "[$p]'s singular is $singular\n" if $debug;
  #print "out: $p\n";
  return $singular if $singular =~/\w/;
}


#bootstrapping using clues such as shared subject different boundary and one lead word
sub additionalbootstrapping{
	my $flag = 0;
	print "Additional bootstrapping\n" if $debug;
	do{
	   $flag = 0;
	   $TAGS = currenttags();
	   $flag += wrapupmarkup(); #shared subject different boundary
	   $flag += oneleadwordmarkup($TAGS);#one lead word
	   $flag += doitmarkup(); 
	}while ($flag>0);
}

sub currenttags{
	my ($id, $sent, $sth, $TAGS, $lead);
  	$sth = $dbh->prepare("select tag from ".$dataprefix."_sentence where (tag != 'ignore' or isnull(tag)) group by tag order by count(sentid) desc");
  	$sth->execute();
  	$TAGS = "";
  	while( my ($tag)=$sth->fetchrow_array()){
    	$TAGS .= $tag."|" if ($tag=~/\w+/);
  	}
  	chop($TAGS);
  	return $TAGS;
}

#skip and/or cases
#skip leads with $stop words
sub doitmarkup{
	my ($id, $sent, $sth, $TAGS, $lead, $tag);
	my $sign = 0;
	print "doit markup\n" if $debug;
  	$sth = $dbh->prepare("Select sentid, lead, sentence from ".$dataprefix."_sentence where isnull(tag) or tag='' or tag='unknown'");
	$sth->execute();
	while(($id, $lead, $sent) = $sth->fetchrow_array()){
		if($sent=~/^.{0,40} (nor|or|and|\/)/){next;}
		if($lead=~/\b($stop)\b/){next;}
		print "sentid: $id: " if $debug;
 		($tag, $sign) = doit($id);   #for cases before relevant knowledge was learned.
    	if($tag =~/\w/){
      		tag($id, $tag);
    	}
	}
	return $sign;
 }

sub oneleadwordmarkup{
	my $TAGS = shift;
	my ($id, $sent, $sth, $lead);
	my $tags = $TAGS."|";
	my $sign = 0;
		
	print "one lead word markup\n" if $debug;
	$sth = $dbh->prepare("Select sentid, lead, sentence from ".$dataprefix."_sentence where isnull(tag) and lead not like '% %'");
	$sth->execute();
	while(($id, $lead, $sent) = $sth->fetchrow_array()){
		if($tags=~/\b$lead\|/){
			tag($id, $lead);
			$sign += update($lead, "n", "-", "wordpos", 1);
		}#else{
		#	tag($id, "unknown");
		#}
	}
	return $sign;
}





#for the remaining of sentences that do not have a tag yet,
#look for lead word co-ocurrance, use the most freq. co-occured phrases as tags

#e.g. plication induplicate (n times) and plication reduplicate (m times) => plication is the tag and a noun
#e.g. stigmatic scar basal (n times) and stigmatic scar apical (m times) => stigmatic scar is the tag and scar is a noun.
#what about externally like A; externally like B, functionally staminate florets, functionally staminate xyz?
sub wrapupmarkup{
  my ($sth, $sth1, $id,$id1, $lead, @words1, @words,@words2, $match, $flag, $ptn, $b, $ld);
  print "wrapupmarkup\n" if $debug;
  my $sign = 0;
  my $checked = "#";
  #find n-grams, n > 1
  $sth1 = $dbh->prepare("Select sentid, lead from ".$dataprefix."_sentence where isnull(tag)  and lead regexp \".* .*\" order by length(lead) desc" );#multiple-word leads
  $sth1->execute();
  while(($id1, $lead) = $sth1->fetchrow_array()){
	  if($checked=~/#$id1#/){next;} 
      @words = split(/\s+/, $lead);
      @words1 = @words; #words in lead 1
      $words[@words-1] = "[^[:space:]]+\$"; #create the pattern to find other sentences sharing the same subject with different boundary words 
      $match = join(" ", @words);
      $sth = $dbh->prepare("Select distinct lead from ".$dataprefix."_sentence where lead regexp \"^".escape($match)."\" and isnull(tag)" );
      $sth->execute();
      if($sth->rows > 1){ # exist x y and x z, take x as the tag
          $match =~ s# \[\^\[.*$##; #shared
          $sth = $dbh->prepare("Select sentid, lead from ".$dataprefix."_sentence where lead like \"".$match."%\" and isnull(tag)" );
          $sth->execute();

          @words = split(/\s+/,$match); #shared part
          $ptn = getPOSptn(@words);#get from ".$dataprefix."_wordpos
          my $wnpos = checkWN($words[@words-1], "pos");
          if($ptn =~ /[nsp]$/ || ($ptn =~/\?$/ && $wnpos  =~ /n/) ){ #functionally staminate x vs. y stops here. @words = "functionally statminate"
	          while(($id, $ld) = $sth->fetchrow_array()){
	          	my @words2 = split(/\s+/, $ld); #words in ld
	          	if(@words2 > @words && getPOSptn($words2[@words]) =~/[psn]/){ #very good apples, don't cut after good.
	          		my $nb = @words2 > @words+1? $words2[@words+1] : ""; #may need to skip two or more words to find a noun.
	          		splice(@words2, @words+1); #remove from @words+1
	          		my $nmatch = join(" ", @words2);
	          		tag($id, $nmatch) ; #save the last word in $match in noun?
	          		tag($id1, $match);
               		$sign += update($words2[@words2-1], "n", "-", "wordpos", 1);
               		$sign += update($nb, "b", "", "wordpos", 1) if $nb ne "";
               		$sign += update($words1[@words1-1], "b", "", "wordpos", 1);
	          	}else{
	          	    $b = @words2> @words? $words2[@words]: ""; 
    		   		tag($id, $match) ; #save the last word in $match in noun?
    		   		tag($id1, $match); #Nov. 18 Hong added
               		$sign += update($words[@words-1], "n", "-", "wordpos", 1); #shared tag
               		$sign += update($b, "b", "", "wordpos", 1) if $b ne ""; 
               		$sign += update($words1[@words1-1], "b", "", "wordpos", 1);
               	}
               	$checked .= $id."#";
              }
           }else{
	           while(($id) = $sth->fetchrow_array()){
    	          #tag($id, "unknown");
    	          $checked .= $id."#";
              }
            }
      }else{
         #tag($id1, "unknown");
         $checked .= $id1."#";
      }
    }#while
  
  return $sign;
}

#escape [] {} and () for mysql regexp, not perl regrexp.
sub escape{
	my $line = shift;
	$line =~ s#([\(\)\[\]\{\}\.\|\-\+\?\'\*])#\\\\\1#g;
	return $line;	
}


#check for sentence table for exsiting tags
#take up to the first n, if it is within 3 word range from the starting of the sentence
#else "unknown";
#sub defaultmarkup{
#	my $TAGS = shift;
#	my ($id,$sent, $sth);
#	my (@words, $tag, $word, $count, $ptn);
# 	print "in default markup: $sent\n" if $debug;
  
#  	$sth = $dbh->prepare("Select sentid, sentence from ".$dataprefix."_sentence where isnull(tag)");
#	$sth->execute();
#	while(($id, $sent) = $sth->fetchrow_array()){
#  		assigndefaulttag($id, $sent, $TAGS);
#  	}
#  	 $sth = $dbh->prepare("update ".$dataprefix."_sentence set tag = 'unknown' where isnull(tag)");
#	 $sth->execute();
#}

sub assigndefaulttag{
	my ($id,$sent, $TAGS) = @_;
	my (@words, $tag, $word, $count, $ptn);
	@words = split(/[,:;.]/,$sent);
  	$words[0] =~ s#\W# #g;
	$words[0] =~ s#(\(? ?\d[\d\W]*)# NUM #g;
	$words[0] =~ s#\b($NUMBERS|_)+\b# NUM #g; #3/12/09
	$words[0] =~ s#\s+$##;
	$words[0] =~ s#^\s+##;
  	@words = split(/\s+/, $words[0]);
	$sent = join(" ", @words);
  	if($sent =~/^\s*(in|on) /i){
    	return;
  	}

  	if($sent =~/^\w*\s?\b($TAGS)\b/i){
		$tag = substr($sent, $-[0], $+[0]-$-[0]);
		print "$tag is found in TAGS \n" if $debug;
		tag($id, $tag);return;
	}

  #take the first noun/nnp as default tag
  splice(@words, 4);
  my ($tag1, $tag2);
  my $ptn = getPOSptn(@words);
  my $n1 = 5; #my $n2 = 5;
  if($ptn =~ /^(.*?[psn]).*/){
    $n1 = length($1);
    splice(@words, $n1);
    $tag1 = join(" ", @words);
	#taking up to the first stop word is not a good idea for fossil_2000, e.g small to medium size; => <small>
    #my $t = join(" ", @words);
    #if ($t =~ /(.*?)\b($NounHeuristics::STOP)\b/){
    #  my @t = split(/ /, $1);
    #  $n2 = @t;
    #  splice(@words, $n2);
    #  $tag2 =  join(" ", @words);
    #}
    #my $TAGSTRING = $TAGS;
    #$TAGSTRING =~ s#[)|(]# #g;
    #if($tag1 !~/\w/ and $tag2 =~/\w/){
    # $tag = $tag2;
    #}elsif($tag2 !~/\w/ and $tag1 =~/\w/){
    #  $tag = $tag1;
    #}else{
    #  if($TAGSTRING =~ /\b$tag1\b/i and $TAGSTRING !~ /\b$tag2\b/i){
    #     $tag = $tag1;
    #  }elsif($TAGSTRING =~ /\b$tag2\b/i and $TAGSTRING !~ /\b$tag1\b/i){
    #     $tag = $tag2;
    #  }else{
    #     $tag = $n1 < $n2 ? $tag1 : $tag2;
    #  }
    #}
    $tag = $tag1;
    print " up to first [psn]. <$tag> is used\n" if $debug;
    tag($id,$tag);
    return;
  }elsif($ptn =~ /^(\??)(\??)b/){   #check WNPOS for pl.
    my $i1 = $-[1];
    my $i2 = $-[2];
    if(length($ptn)>1){ #forget about $ptn = b or b?
   	if(getnumber($words[$i2]) eq "p"){
   		if ($words[$i2-1] ne $words[$i2]){ tag($id, $words[$i2-1]." ".$words[$i2]);}
   		#if ($words[$i2-1] eq $words[$i2]){ tag($id, $words[$i2]);} #$ptn = "b"; Hong Nov 18.
   		print " pos determined tag <$words[$i2-1] $words[$i2]>" if $debug;
   	}elsif(getnumber($words[$i1]) eq "p"){
   		tag($id, $words[$i1]);
   		print " pos determined tag <$words[$i1]>" if $debug;
  	}
    }
  	return;#Nov. 18 Hong
   }

  splice(@words, 3);     #keep first three words
  if($ptn =~/^(b\?)b/ and $words[2] eq "of"){#save ? as a noun?
    update($words[1], "n", "-", "wordpos", 1);
    splice(@words, 2);
    $tag = join(" ", @words);
    tag($id, $tag);   #first two words for tag
    return;
  }
  #check for the first pl
  $count = 0;
  foreach $word (@words){
     if($count++ > 4) {last;}
     my $p = getnumber($word);
     if($word !~/\b($stop)\b/ ||  $p ne "p"){
       $tag .= $word." ";
     }elsif ($p eq "p"){
       $tag .= $word;
	     print "a likely [p] $tag is used\n" if $debug;
       tag($id, $tag);
       return;
     }
   }
   return;
}



########return a positive number if any new discovery is made in this iteration
########use rule based learning first for easy cases
########use instance based learning for the remining unsolved cases
sub discover{
	my $status = shift;
  	#$status .= "|start" if $status eq "normal";
	my ($sid, $sentence, @startwords, $pattern, @matched, $round, $sth, $new, $lead, $tag, $newdisc);
	$sth = $dbh->prepare("select sentid,sentence,lead,tag from ".$dataprefix."_sentence where (tag != 'ignore' or isnull(tag)) and status = '$status'");
    $sth->execute();
	while(($sid, $sentence, $lead, $tag) = $sth->fetchrow_array()){
		if(ismarked($sid)){next;} #marked, check $sid for most recent info.
		@startwords = split(/\s+/,$lead);
		print "\n>>>>>>>>>>>>>>>>>>start an unmarked sentence [$sentence]\n" if $debug;
		$pattern = buildpattern(@startwords);
		print "Build pattern [$pattern] from starting words [@startwords]\n" if $debug;
		if($pattern =~ /\w+/){
			@matched = matchpattern($pattern, $status, 0); #ids of untagged sentences that match the pattern
			$round = 1;
      		$new = 0;
			do{
			    print "####################round $round: rule based learning on ".@matched." matched sentences\n" if $debug;
			    $new = rulebasedlearn(@matched); #grow %NOUNS, %BDRY, tag sentences in %SENTS, record %DECISIONS
          		$newdisc += $new;
				print "##end round $round. made $new discoveries\n" if $debug;
				$round++;
			}while ($new > 0);
			#$round = 1;
			#$new = 0;
			#do{
			#    print "~~~~~~~~~~~~~~~~~~~~round $round: instance based learning on matched sentences\n" if $debug;
			#    $new = instancebasedlearn(@matched);
			#	$round++;
			#}while($new > 0);
		}
	}
 return $newdisc;
}

#sentences that match the pattern
sub matchpattern{
my($pattern, $status, $hastag) = @_;
my ($stmt, $sth, @matchedids, $sentid, $sentence);
if($hastag == 1){
$stmt = "select sentid,sentence from ".$dataprefix."_sentence where status=\"$status\" and !isnull(tag)";
}else{
$stmt = "select sentid,sentence from ".$dataprefix."_sentence where status=\"$status\" and isnull(tag)";
}
$sth = $dbh->prepare($stmt);
$sth->execute();
while(($sentid, $sentence) = $sth->fetchrow_array()){
	push(@matchedids, $sentid) if($sentence =~/$pattern/i)
}
return @matchedids;
}

####go over all un-decided sentences, solve these cases by using instance-based learning
#endings of marked sentences: "<d".$DECISIONID."-".lc $tag.">"
sub ismarked{
	my $id = shift;
	my $sth;
	$sth=$dbh->prepare("select * from ".$dataprefix."_sentence where sentid=".$id." and !isnull(tag)");
    $sth->execute();
	return $sth->rows != 0;
}
##@todo
#sub instancebasedlearn{
#	my @sentids = @_;
#	my ($sent, $sentid, $lead, $tag, @words, $ptn, $index, $wordptn, $unique, $temp);
#	my ($value, @unknown,$unknown, $text, $word, $pos, $role, $certainty, $sth, @matched, $flag, $sid, $new);
#	my %register=("n","","s","","p","","b","","?","");
#	foreach $sentid (@sentids){
#               $flag = 0; #no new information may be found in matched cases
#		$sth = $dbh->prepare("select sentence, lead, tag from ".$dataprefix."_sentence where sentid=".$sentid);
#		$sth->execute();
#		($sent, $lead, $tag) = $sth->fetchrow_array();
#		if(!ismarked($sentid)){
#			print "For unmarked sentence: $sent\n" if $debug;
#			@words = split(/\s+/,$lead);
#			$ptn = getPOSptn(@words);
#			print "\t leading words: @words\n\t pos pattern: $ptn\n" if $debug;
#			$index = index($ptn, "?");
#			if(rindex($ptn,"?") == $index && length $ptn > 1 && $index >=0){ #only one unknown
#   			$wordptn = buildwordpattern(join(" ", @words), $ptn); # nn? => flower buds \w+
#				print "\t word pattern: $wordptn\n" if $debug;
#    			@matched = matchwordptn($wordptn, @sentids); # ids of sentences matching the pattern: tagged or untagged, pos known or unknown
#    			#check the leading words' POSs in @matched
#        		foreach $sid (@matched){
#        			$sth = $dbh->prepare("select lead from ".$dataprefix."_sentence where sentid=$sid");
#					$sth->execute();
#					($lead) = $sth->fetchrow_array();
#					@words = split(/\s+/, $lead);
#					$temp = $words[$index]; #the word at the position of "?"
#					                        #we hope one of these words have a known pos, so we can infer others' pos
#					if($unique !~ /\b$temp\b/){
#					    $unique .= $temp." ";
#						#@todo==fix syntax
#       				($pos, $role, $certainty) = checkposinfo($temp,"one");
#        				if($pos ne "2" && $pos ne "?"){
#        					$register{$pos} .= $sid." \$\$\$\$ ";
#							$flag = 1;
#        				}elsif($pos eq "?"){
#							$register{"?"} .= $sid." \$\$\$\$ ";
#						}else{
#						    print "$pos: $_\n" if $debug;
#						}
#					}
#       		}
#				if($flag == 0) {
#				    print "\t no new POS discovered by instance-based learning\n" if $debug;
#				    return $new;
#				}
#     			@unknown = split(/\s*\$\$\$\$\s*/,$register{"?"});
#				delete($register{"?"});
#				foreach $unknown (@unknown){
#						($pos, $role) = selectpos($unknown,$word,$index,%register);
#						$new += update($word, $pos, $role, "wordpos") if($pos =~/\w/);
#
#				}
#			}
#		}
##@todo                  check for and resolve any conflict with marked @matched sentences
#
#		if($new > 0){
#			foreach (@matched){
#				markup($_) if (!ismarked($_));
#			}
#		}
#	}
#	return $new;
#}

sub matchwordptn{
my($wordptn, @sentids) =@_;
my(@results, $sth, $sent);
foreach (@sentids){
 $sth = $dbh->prepare("select sentence from ".$dataprefix."_sentence where sentid=$_");
 $sth->execute();
 $sent = $sth->fetchrow_array();
 push(@results, $_) if $sent =~ /$wordptn/i
}
}

##look in %register for the most similar instance to $text
##to find $pos and $role for $word,
##and to find $tag for $text
sub selectpos{
	my ($text, $word, $index, %register) = @_;
	my ($ipos, $inst, $icertainty,$irole,$sim, $pos, $role, $tag, @instances);
	my $top = 0;

	print "\t seen examples in hash:\n" if $debug;
	print map{"\t\t $_=> ".substr($register{$_}, 0, 50)."\n"} keys %register if $debug;
	foreach $ipos (keys(%register)){
		@instances = split(/\$\$\$\$ /,$register{$ipos});#with known pos
		foreach $inst (@instances){
		#"<d".$DECISIONID."-".$tag.">"@todo
		 	if($inst =~ /(.*?)(?:<d\d+-([a-z]+)>)?\s*\(([0-9.]+)-([-_+]?)\)$/){
				$sim = sim($text, $1, $word, $index);
				$icertainty = $3;
			    $irole = $4;
				if($top < $sim){
					$top = $sim;
					$role = $irole;
					$pos = $ipos;
				}
			}
		}
	}
	if($pos =~ /[ps]/){
	    $pos = getnumber($word);
	}
	print "\t selected pos for $word: $pos\n" if $debug;
	return ($pos,$role);
}
#check the before and after word of $word
sub sim{
	my($sent1, $sent2,$word, $index, @a, @b) = @_;
	#$sent1: {xx?}xx?abc
	#$sent2: {xxy}xxydef
	#if pos(a) = pos(d) +1
	#if a = d +2
	my ($ab, $aa, $bb, $ba, @t1, @t2);
	my $sim = 0;
	if($sent1 =~ /\d+\{.*?\}(.*)/){
		@a = split(/\s+/,$1);
		$aa = $a[$index+1];
		$ab = $a[$index-1];
	}
	if($sent2 =~ /\d+\{.*?\}(.*)/){
		@b = split(/\s+/,$1);
		$ba = $b[$index+1];
		$bb = $b[$index-1];
	}
	if($ba eq $aa && $ba =~ /\w/){ $sim++;}
	if($bb eq $ab && $bb =~ /\w/){ $sim++;}
	if($sim != 0){
		print "\t\t similarity between ".substr($sent1, 0, 20)." and ".substr($sent2, 0, 20)." is $sim\n" if $debug;
	    return $sim;
    }
	#@todo
    @t1 = checkpos($ba, "one");
	@t2 = checkpos($aa, "one");
	if(shift (@t1) eq shift (@t2)){ $sim += 0.5;}
	@t1 = checkpos($bb, "one");
	@t2 = checkpos($ab,"one");
	if(shift (@t1) eq shift (@t2)){ $sim += 0.5;}
	if($sim != 0){
	      print "\t\t similarity between ".substr($sent1, 0, 20)." and ".substr($sent2, 0, 20)." is $sim\n" if $debug;
		  return $sim;
	}
	print "\t\t similarity between ".substr($sent1, 0, 20)." and ".substr($sent2, 0, 20)." is 0\n" if $debug;

}

#mark up a sentence whose leading words have known pos. return the tag
sub markup{
	my $sentid = shift;
	my ($tag, $sign) = doit($sentid);
	tag($sentid, $tag);
}
#the length of the ptn must be the same as the number of words in @words
#if certainty is < 50%, replace POS with ?.
sub getPOSptn{
	my @words = @_;
	my ($pos, $certainty, $role, $ptn, @posinfo, $word);
	foreach $word (@words){
		@posinfo = checkposinfo($word,"one");
		#@todo:test
		#$pos = "?" if (@posinfo > 1); #if a word is marked as N and B, make it unknown
		($pos, $role, $certainty)= ($posinfo[0][0],$posinfo[0][1],$posinfo[0][2]);
		if ($pos ne "?" && tovalue($certainty) <= 0.5){
    		print "$word 's POS $pos has a certainty ".tovalue($certainty)." (<0.5). This POS is ignored\n" if $debug;
		   $pos = "?" ;
		}
		$ptn .= $pos;  	#pbb,sbb, sb?
	}
	#chop($ptn);
	return $ptn;
}



########return a positive number if anything new is learnt from @source sentences
########by applying rules and clues to grow %NOUNS and %BDRY and to confirm tags
########create and maintain decision tables
sub rulebasedlearn{
	my @sentids = @_; #an array of sentences with similar starting words
	my ($sign, $new, $tag, $sentid);
	foreach $sentid (@sentids){
    	if(!ismarked($sentid)){#without decision ids
			($tag, $new) = doit($sentid);
			tag($sentid, $tag);
			$sign +=$new;
		}
	}
	return $sign;
}

#update ".$dataprefix."_wordpos table (on certainty) when a sentence is tagged for the first time.
#this update should not be done when a pos is looked up, because we may lookup a pos for the same example multiple times.
#if the tag need to be adjusted (not by doit function), also need to adjust certainty counts.

sub doit{
	my ($sentid) = shift;
	my ($tag, $sign, $ptn, $i,$t, @ws, @tws, @cws, $pos, $certainty);
	my ($role, $start, $end, @t, $sentence, $sth, $lead, $sent);
	$sth = $dbh->prepare("select sentence, lead from ".$dataprefix."_sentence where sentid = $sentid");
	$sth->execute();
	($sentence, $lead) = $sth->fetchrow_array();
	
	#if($lead =~/\b(and|or|nor)\b/){
	#	return ($tag,$sign);
	#}

	@ws = split(/\s+/,$lead);
	$ptn = getPOSptn(@ws);
	print "\nsentence: $sentence\n" if $debugp;

	if($ptn =~ /^[pns]$/){
    	#single-word cases, e.g. herbs, ...
    	$tag = $ws[0];
		$sign += update($tag, $ptn,"-", "wordpos", 1);
		print "Directly markup with tag: $tag\n" if $debugp;
	}elsif($ptn =~ /ps/){#questionable 
	    #@todo test: stems multiple /ps/, stems 66/66, multiple 1/1
		#Hong Dec 10: don't test on certainty, directly discount s.
		print "Found [ps] pattern\n" if $debugp;
		my $i = $+[0];    #end of the matching
		my $s = $ws[$i-1];
		$i = $-[0]; #start of the matching
		my $p = $ws[$i];
		#my @p = checkposinfo($p, "one");
		#my @s = checkposinfo($s, "one");
		#my ($pcertainty, $pcertaintyu, $pcertaintyl,$scertainty, $scertaintyu, $scertaintyl);
		#($pos, $role, $pcertainty) = ($p[0][0], $p[0][1], $p[0][2]);
		#($pos, $role, $scertainty) = ($s[0][0], $s[0][1], $s[0][2]);
    	#($pcertaintyu, $pcertaintyl) = split(/\//, $pcertainty);
    	#($scertaintyu, $scertaintyl) = split(/\//, $scertainty);
    	#$pcertainty = $pcertaintyu/$pcertaintyl;
    	#$scertainty = $scertaintyu/$scertaintyl;
		#if($pcertainty >= $scertainty && $pcertaintyl >= $scertaintyl*2){
		   #discount($s, "s", "b", "byone");
       	   #print "discount $s pos of s\n" if $debugp;
		   @tws = splice(@ws, 0, $i+1);   #up to the "p" inclusive
		   $tag = join(" ",@tws);
		   print "\t:determine the tag: $tag\n" if $debugp;
		   $sign += update($p, "p", "-", "wordpos", 1);
		   $sign += updatenn(0, $#tws+1, @tws); #up to the "p" inclusive
		   #if($scertainty * $scertaintyl < 2){
		     $sign += update($s, "b","", "wordpos", 1); #would discount $s's s pos s=>b
		   #}
		#}elsif($pcertainty < $scertainty && $pcertaintyl < $scertaintyl*2){
		#   discount($p, "p", "s", "byone");
       	#   print "discount $p pos of p\n" if $debugp;
		#   $sign += update($s, "s", "", "wordpos");
       	#   #@todo: determine the tag?
		#}		
   }elsif($ptn =~/p(\?)/){#case 3,7,8,9,12,21: p? => ?->%BDRY
	  	#use up to the pl as the tag
		#if ? is not "p"->%BDRY
		#save any NN in %NOUNS, note the role of Ns
		$i = $-[1];#index of ?
		#what to do with "flowers sepals" when sepals is ?
		print "Found [p?] pattern\n" if $debugp;
		if(getnumber($ws[$i]) eq "p"){    # pp pattern
		    #$tag = $ws[$i-1];
       	   	$tag = $ws[$i];
		   	$sign += update($tag, "p","-", "wordpos", 1);
           	my $sth = $dbh->prepare("insert into ".$dataprefix."_isA (instance, class) values (\"".$tag."\",\"".$ws[$i-1]."\")");
           	$sth->execute();
           	print "\t:[p p] pattern: determine the tag: $tag\n" if $debugp;
		}else{
		    @cws = @ws;
    		@tws = splice(@ws,0,$i);#get tag words, @ws changes as well
    		$tag = join(" ",@tws);
    		print "\t:determine the tag: $tag\n" if $debugp;
    		print "\t:updates on POSs\n" if $debugp;
    		$sign += update($cws[$i], "b", "", "wordpos", 1);
			$sign += update($cws[$i-1],"p", "-", "wordpos", 1);
			$sign += updatenn(0,$#tws+1, @tws);
		}
	}elsif($ptn =~ /[psn](b)/){#case 2,4,5,11,6,20: nb => collect the tag
		#use up to the N before B as the tag
		#save NN in %NOUNS, note the role of Ns
		#anything may be learned from rule 20?
		if($ptn =~ /^sbp/){
			print "Found [sbp] pattern\n" if $debugp;
			@cws = @ws;
			$tag = join(" ", splice(@cws, 0, 3));
			print "\t:determine the tag: $tag\n" if $debugp;
		}else{
			print "Found [[psn](b)] pattern\n" if $debugp;
			$i = $-[1]; #index of b
			$sign += update($ws[$i], "b","", "wordpos", 1);
			@cws = @ws;
			@tws = splice(@ws,0,$i);#get tag words, @ws changes.
			$tag = join(" ",@tws);
			$sign += update($cws[$i-1], substr($ptn, $i-1, 1), "-", "wordpos", 1);
			$sign += updatenn(0, $#tws+1,@tws);
			print "\t:determine the tag: $tag\n" if $debugp;
			print "\t:updates on POSs\n" if $debugp;
		}
	}elsif($ptn =~ /([psn][psn]+)/){#case 1,3,10,19: nn is phrase or n2 is a main noun
		#if nn is phrase or n2 is a main noun
		#make nn the tag
		#the word after nn ->%BDRY
		#save NN in %NOUNS, note the role of Ns
		print "Found [[psn][psn]+] pattern\n" if $debugp;
		#3/5/09: check the pattern for words following the ptn
		$start = $-[1];
		$end = $+[1]; #the last of the known noun
		@cws = @ws; #make a copy of @ws

		my ($moren, $moreptn, $bword) = getNounsAfterPtn($sentence, $end);
		my @moren = split(/\s+/, $moren);
    	#if contain pp, take the last p as the tag
    	#otherwise, take the whole pattern
    	if($ptn =~ /pp/){ 
    		if($moreptn =~/^p*(s)/){
    			#find $lastp and $safterp, then reset $safterp to "b"    	
    			my $isafterp = $-[1];
    			my $ilastp = $isafterp-1;
    			my $safterp = $moren[$isafterp];
    			my $lastp = $ilastp >=0? $moren[$ilastp] : "";
    			$bword = $safterp;
    			$tag = $lastp =~/\w/ ? $lastp : $ws[rindex($ptn,"p")];
    			$sign += update($safterp,"b","", "wordpos", 1); #discount s to b
			}elsif($moreptn =~/^(p+)/){
    			my $ilastp = $+[1];
    			$tag = $moren[$ilastp];
			}else{
    	   		my $i = rindex($ptn, "p");
       			$tag = $ws[$i];   #may be rejected later
    		}
       		@t = checkposinfo($tag, "one");#last p is the tag
       		#my $sth = $dbh->prepare("insert into ".$dataprefix."_isA (instance, class) values (\"".$tag."\",\"".$ws[$i-1]."\")");
       		#$sth->execute();
    	}else{#not pp
    		#the whole ptn + $moren
       		@tws = splice(@ws, 0, $end);#get everything up to and include nn
       		$tag = join(" ",@tws);      #may be rejected later
       		$tag .= " ".$moren if $moren =~/\w/;
       		@t = checkposinfo(substr($tag, rindex($tag, " ")+1), "one");
    	}
    	($pos, $role, $certainty) = ($t[0][0], $t[0][1], $t[0][2]);  #last n
    	#if($pos=~/[psn]/ && $role =~ /[-+]/){
    	if($pos=~/[psn]/){#3/15/09 relax this condition
    		@t = split(/\s+/,$sentence);
    		$sign += update($bword,"b","", "wordpos", 1); #@todo:test
    		#update pos for each word in the ptn and morenptn
    		$ptn = substr($ptn, $start, $end-$start); #5/01/09
    		my $tptn = $ptn.$moreptn;#total pattern
    		for(my $i = $start; $i < length($tptn); $i++){
    			$sign += update($t[$i], substr($tptn, $i, 1), "_", "wordpos", 1) if $i != length($tptn) -1 ;
    			$sign += update($t[$i], substr($tptn, $i, 1), "-", "wordpos", 1) if $i == length($tptn) -1 ;
    		}
			$sign += updatenn(0, length($tptn), @t) if @t > 1;
    	}#else{ #5/30/09
    	#	print "\t:$tag not main role, reset tag to null\n" if $debugp;
		#    $tag = "";
  		#}		

		#before 3/5/09
		#$start = $-[1];
		#$end = $+[1]; #the last of the known noun
		#@cws = @ws;

    	##if contain pp, take the last p as the tag
    	##otherwise, take the whole pattern
    	#if($ptn =~ /pp/){
    	#		my $i = rindex($ptn, "p");
       	#		$tag = $ws[$i];   #may be rejected later
       	#		@t = checkposinfo($tag, "one");#last p is the tag
       	#		$end = $i+1;
       	#		my $sth = $dbh->prepare("insert into ".$dataprefix."_isA (instance, class) values (\"".$tag."\",\"".$ws[$i-1]."\")");
       	#		$sth->execute();
    	#}else{#not pp
       	#	#@tws = splice(@ws,$start, $end-$start);#get nn tag words
       	#	@tws = splice(@ws, 0, $end);#get everything up to and include nn
       	#	$tag = join(" ",@tws);      #may be rejected later
       	#	@t = checkposinfo($tws[@tws-1], "one");
    	#}
    	#($pos, $role, $certainty) = ($t[0][0], $t[0][1], $t[0][2]);  #last n
    	#if($pos=~/[psn]/ && $role =~ /[-+]/){
    	#	#the word after nn ->%BDRY
    	#	@t = split(/\s+/,$sentence);
    	#	$sign += update($t[$end],"b","", "wordpos"); #@todo:test
		#	$sign += update($cws[$start], substr($ptn, $start, 1), "", "wordpos") if $start < $end;
		#	$sign += updatenn(0, $#tws+1, @tws) if @tws > 1;
    	# 	$sign += update($cws[$start+1], substr($ptn, $start+1, 1), "", "wordpos") if $start+1 < $end;
    	#}else{
    	#	print "\t:$tag not main role, reset tag to null\n" if $debugp;
		#    $tag = "";
  		#}
  		
		#earlier
		#if($ptn =~/pp/){
    	#	$tag = $ws[$start];
		#	  @t = checkposinfo($tag, "one");#first p is the tag
		#}else{
    	#		@tws = splice(@ws,$start, $end-$start);#get tag words
    	#		$tag = join(" ",@tws);
		#	  @t = checkposinfo($tws[@tws-1], "one");
		#}
 	  	#($pos, $role, $certainty) = ($t[0][0], $t[0][1], $t[0][2]);  #last n
    	#if($pos=~/[psn]/ && $role =~ /[-+]/){   #pp got processed again!?
    		#the word after nn ->%BDRY
    	#		@t = split(/\s+/,$sentence);
    	#		$sign += update($t[$end],"b",""); #@todo:test
		#	  $sign += update($cws[$start], substr($ptn, $start, 1), "");
		#	  $sign += updatenn(0, $#tws+1, @tws);
    	#	  $sign += update($cws[$start+1], substr($ptn, $start+1, 1), "");
    	#}else{
    	#		print "\t:$tag not main role, reset tag to null\n" if $debug;
		#	  $tag = "";
  		#}
   		print "\t:determine the tag: $tag\n" if $debugp;
	#}elsif($ptn =~ /b\?([psn])$/ or $ptn=~/\?b([psn])$/){#case 16, 25
	}elsif($ptn =~ /b[?b]([psn])$/ or $ptn=~/[?b]b([psn])$/){#case 16, 25 #3/5/09
		#if n can be a main noun
		#make up to n the tag
		#the word after n ->%BDRY
		print "Found [b?[psn]\$] or  [?b[psn]\$] pattern\n" if $debugp;
		$end = $-[1];#index of n
		my $cend = $end;
		my ($moren, $moreptn, $bword) = getNounsAfterPtn($sentence, $end+1);
		@ws = tokenize($sentence, "firstseg");
		@cws = @ws;
		$end += length($moreptn);
		@tws = splice(@cws, 0, $end+1);
		$tag = join(" ",@tws);
		#@t = checkposinfo($tws[$end],"one");#n's posinfo
		#($pos, $role, $certainty) = ($t[0][1], $t[0][1], $t[0][2]);
		#if($role =~ /[-+]/ || $pos eq "p"){#3/15/09 remove this condition
			print "\t:updates on POSs\n" if $debugp;
			$sign += update($bword,"b","", "wordpos", 1) if $bword =~/\w/; #test
			my $tptn = $ptn.$moreptn;
			for(my $i =$cend; $i < length($tptn); $i++){
				$sign += update($ws[$i], substr($tptn, $i, 1), "_", "wordpos", 1) if $i != length($tptn) - 1;
				$sign += update($ws[$i], substr($tptn, $i, 1), "-", "wordpos", 1) if $i == length($tptn) - 1;
			}
		#}else{
		#	print "\t:$cws[$end] not main role, reset tag to null\n" if $debugp;
		#	$tag = "";
		#}
		
		#before 3/5/09
		#$end = $-[1];#index of n
		#@cws = @ws;
		#@tws = splice(@ws, 0, $end+1);
		#$tag = join(" ",@tws);
		#@t = checkposinfo($cws[$end],"one");#n's posinfo
		#($pos, $role, $certainty) = ($t[0][1], $t[0][1], $t[0][2]);
		#if($role =~ /[-+]/){
		#	#the word after n ->%BDRY
      	#	@t = tokenize($sentence, "firstseg");
		#	print "\t:updates on POSs\n" if $debugp;
		#	$sign += update($t[$end+1],"b","", "wordpos"); #test
		#	#$sign += update($cws[$end-2], "b", "");
		#	$sign += update($cws[$end], substr($ptn, $end, 1), $role, "wordpos");
		#}else{
		#	print "\t:$cws[$end] not main role, reset tag to null\n" if $debugp;
		#	$tag = "";
		#}
		print "\t:determine the tag: $tag\n" if $debugp;
	}elsif($ptn =~ /^s(\?)$/){
	    #? =>b
		#@todo:test, need test
		$i = $-[1];#index of ?
		print "Found [^s?\$] pattern\n" if $debugp;
		my $wnp = checkWN($ws[$i], "pos");
		if($wnp =~/p/){ #"hinge teeth,"
			$tag = $ws[$i-1]." ".$ws[$i];
			print "\t:determine the tag: $tag\n" if $debugp;
			print "\t:updates on POSs\n" if $debugp;
			my $n = getnumber($ws[$i]);
			$sign += update($ws[$i], $n, "-", "wordpos", 1);			
		}else{
			$tag = $ws[$i-1];
			print "\t:determine the tag: $tag\n" if $debugp;
			print "\t:updates on POSs\n" if $debugp;
			$sign += update($ws[$i], "b", "", "wordpos", 1);
			$sign += update($ws[$i-1], "s", "-", "wordpos", 1);
		}
	}elsif($ptn =~ /^bs$/){
	    $tag = join(" ", @ws);
		$sign += update($ws[0], "b", "", "wordpos", 1);
		$sign += update($ws[1], "s", "-", "wordpos", 1);
	}elsif($ptn =~ /^bp$/){
	    $tag = join(" ", @ws);
		$sign += update($ws[0], "b", "", "wordpos", 1);
		$sign += update($ws[1], "p", "-", "wordpos", 1);
	}elsif($ptn =~ /^\?(b)/){#case 8,17,22,23,24,26: ?b => ?->%NOUNS. Note: ? could also be a type modifier such as middle as in middle sessile
		#?->%NOUNS, note the role of ?
		#use up to ? as the tag ===>
		#$ptn=~/\?(b)/
		#		Lead words: Leaves basally white
		#		POS pattern of the lead words: ??b
		#		Found [?(b)] pattern
		#			  :determine the tag: Leaves basally
		#			  :updates on POSs
		#			  for []: old pos [] is updated
		#			  to the new pos [pos:n;certainty:1/1;role:-]
	
		#		Lead words: lateral leaflets often
		#		POS pattern of the lead words: ??b
		#		Found [?(b)] pattern
		#			  :determine the tag: lateral leaflets
		#			  :updates on POSs
		#			  for [lateral]: old pos [] is updated
		#			  to the new pos [pos:s;certainty:1/1;role:-]
		#@todo: check "leaves" and "basally" in a dictionary to determine how to update on their POSs
		print "Found [?(b)] pattern\n" if $debugp;
		
			$i = $-[1];#index of (b)
			$sign += update($ws[$i], "b", "", "wordpos", 1);
	    	@cws = @ws;
			@tws = splice(@ws,0,$i);#get tag words
			$tag = join(" ",@tws);
	    	my $word = $cws[$i-1]; #the "?" word;
	   		if(!followedbyn($sentence, $lead)){	#condition added 4/7/09
	    	#my $wnp = checkWN($word, "pos");
	    	#my ($maincount, $modicount) = getroles($word);
	    	#print "main role = $maincount; modifier role = $modicount\n" if $debugp; #both are empty values
	    	#if(($wnp eq "" || $wnp =~ /[psn]/) && $maincount >= $modicount){#tag is not an adv or adj such as abaxially or inner
			 # print "\t: $word checkWN pos: $wnp\n" if $debug;
			 #print "\t:determine the tag: $tag\n" if $debugp;
			 # print "\t:updates on POSs\n" if $debugp;
			 # $sign += update($cws[$i-1], "n", "-", "wordpos");
			 # $sign += updatenn(0,$#tws,@tws);
			
			#decision revoked Dec 8, 2008 Hong: ?b pattern works well when ? is a plural noun, not so well when ? is a type modifier
	    	#so tighten up the pattern, make a decision only when ? is a plural noun
			
			#my $wnp = getnumber($word);
			#if()$wnp eq "p"){#tag is not an adv or adj such as abaxially or inner
			#  print "\t:determine the tag: $tag\n" if $debugp;
			#  print "\t:updates on POSs\n" if $debugp;
			#  $sign += update($cws[$i-1], "n", "-", "wordpos");
			#  $sign += updatenn(0,$#tws,@tws);
			
			#3/12/09
			my $wnp1 = checkWN($word, "pos");
			my $wnp2 = getnumber($word) if $wnp1 !~/\w/;
			$wnp1 = "" if $wnp1 =~/[ar]/;
			if($wnp1=~/[psn]/ || $wnp2 =~ /[ps]/){#tag is not an adv or adj such as abaxially or inner
				print "\t:determine the tag: $tag\n" if $debugp;
				print "\t:updates on POSs\n" if $debugp;
				$sign += update($cws[$i-1], "n", "-", "wordpos", 1);
				$sign += updatenn(0,$#tws,@tws);
	    	}else{
	    		#$sign += update($word, "b", "", "wordpos"); #line added 4/7/09
			  print "\t:$tag is adv/adj or modifier. skip.\n" if $debugp;
	      	  $tag = "";
	    	}
		}else{#4/7/09
			#$sign += update($word, "b", "", "wordpos");
			print "\t:$tag is adv/adj or modifier. skip.\n" if $debugp;
	      	$tag = "";
		}
	}else{
		print "Pattern [$ptn] is not processed\n" if $debugp;
	}
	return ($tag,$sign);
}
#4/7/09
#return true if lead is followed by a N without any proposition in between
sub followedbyn{
	my ($sentence, $lead) = @_;
	$sentence =~ s#^$lead##;
	my $word;
	my $knownnouns = "";
	my $sth = $dbh->prepare("select word from ".$dataprefix."_wordpos where pos ='p' or pos = 's'");
	$sth->execute() or warn "$sth->errstr\n";
	while(($word) = $sth->fetchrow_array()){
		$knownnouns .= $word."|" if length($word)>0;
	}
	chop($knownnouns);
	if ($sentence =~/(.*?)\b($knownnouns)\b/){
		my $inbetween = $1;
		return 1 if $inbetween !~ /\b($PROPOSITION)\b/;
	}
	return 0;
}

#3/5/09
sub getNounsAfterPtn{
	my ($sentence, $startwordindex) = @_;
	my ($ns, $nptn, $bword);
	my @words = tokenize($sentence, "firstseg");
	@words = splice(@words, $startwordindex);
	my $ptn = getPOSptn(@words);
	#5/01/09
	if($ptn=~/^([psn]+)/ or $ptn=~/^(\?+)/){
		my $end = $+[1]-1;
		$bword = $words[$end+1] if $end+1 < @words;
		my @nwords = splice(@words, 0, $end+1);
		for(my $i = 0; $i<@nwords; $i++){
			my $p = substr($ptn, $i, 1);
			$p = $p eq "?" ? checkWN($nwords[$i], "pos") : $p;
			if ($p =~/^[psn]+$/){ #5/01/09 check 4228 5/23/09 add+ check 5385
				$ns .= $nwords[$i]." ";
				$nptn.=$p;
				#my $role = $i == @nwords-1? "-":"_";
				#$update += update($nwords[$i], "n", "", "wordpos");
			}else{
				$bword = $nwords[$i];
				last;
			}
		} 
	}
	$ns =~ s#\s+$##g;
	#if($ptn=~/^([psn]+)/){
	#	my $end = $+[1]-1;
	#	$nptn = substr($ptn, 0, $end+1);
	#	$bword = $words[$end+1] if $end+1 < @words;
	#	my @nwords = splice(@words, 0, $end+1);
	#	$ns = join(" ", @nwords); 
	#}else{
	#	$bword = $words[0];
	#}
	return ($ns, $nptn, $bword);	
}

sub getroles{
  my $word = shift;
  my ($sth, $maincount, $modicount);
  $sth = $dbh->prepare("select certaintyu from ".$dataprefix."_wordpos where word = \"$word\" and role = \"-\"");
	$sth->execute();
  $maincount = $sth->fetchrow_array();
  $sth = $dbh->prepare("select count from ".$dataprefix."_modifiers where word = \"$word\"");
	$sth->execute();
  $modicount = $sth->fetchrow_array();
  return ($maincount, $modicount);
}
sub tag{
	#my ($sentence, $tag) = @_;
	my($sid, $tag) = @_;
	my $sth;
	if($tag !~ /\w+/){return;}
	if($tag =~ /^($stop)\b/){
		print "\t:tag <$tag> starts with a stop word, ignore tagging requrest\n" if $debug;
		return;
	}
	#$tag = lc $tag;
	if($tag =~ /\w+/){
		if(length($tag) > $taglength){
			$tag = substr($tag, 0, $taglength);
			print "\n tag <$tag> longer than $taglength\n";
		}
	   $sth = $dbh->prepare("update ".$dataprefix."_sentence set tag ='$tag' where sentid =$sid");
	   $sth->execute();
	   print "\t:mark up ".$sid." with tag $tag\n" if $debug;
	}
}


##$mode: 
##either "byone": reduce certainty 1 by 1
##or "all": remove this pos
##
##discount only discount existing pos, do not establish $suggestedpos, which is done seperately, e.g in changePOS
sub discount{
	my($word, $pos, $suggestedpos, $mode) =@_;
	my ($sth1, $cu, $cl, $relatedword, $wordlist, $sth);
	#also need to discount the related words if $word was the source word in prefix-based learning
	$sth1 = $dbh->prepare("select word from ".$dataprefix."_unknownwords where flag = (select flag from ".$dataprefix."_unknownwords where word = '$word')");
	$sth1->execute();
	while(($relatedword) = $sth1->fetchrow_array()){
		$wordlist .= "'".$relatedword."',";
	}
	$wordlist .= "'".$word."'," if ($wordlist !~ /\b$word\b/);
	chop($wordlist);
	print "words related to $word to be discounted: $wordlist \n" if $debug;
	
	#$sth1 = $dbh->prepare("select certaintyu, certaintyl from ".$dataprefix."_wordpos where word=\"$word\" and pos=\"$pos\"");
	$sth1 = $dbh->prepare("select word, certaintyu, certaintyl from ".$dataprefix."_wordpos where word in ($wordlist) and pos='$pos'");
	$sth1->execute() or warn "$sth1->errstr\n";
	while(($word, $cu, $cl) = $sth1->fetchrow_array()){
		if(--$cu <= 0 || $mode eq "all"){
			#remove this record from ".$dataprefix."_wordpos
			$sth = $dbh->prepare("delete from ".$dataprefix."_wordpos where word = '$word' and pos='$pos' ");
			$sth->execute() or warn "$sth->errstr\n";
			#reset this word to unknown in unknownwords
			updateunknownwords($word, "unknown");
			#if $pos is "s" or "p", update ".$dataprefix."_singularplural table
			if($pos =~ /[sp]/){
				$sth = $dbh->prepare("delete from ".$dataprefix."_singularplural where singular = '$word' or plural ='$word' ");
				$sth->execute() or warn "$sth->errstr\n";
				#set sentences already marked as $word to NULL:
				#$sth = $dbh->prepare("update ".$dataprefix."_sentence set tag = NULL where tag = '$word' ");
				#$sth->execute() or warn "$sth->errstr\n";
				#print "reset sentences marked as $word to NULL\n" if $debug;
			}
			#record this into discouted table
			$sth = $dbh->prepare("insert into ".$dataprefix."_discounted values ('$word', '$pos', '$suggestedpos')");
			$sth->execute() or warn "$sth->errstr\n";
		}else{
			#update
			$sth = $dbh->prepare("update ".$dataprefix."_wordpos set certaintyu=$cu where word=\"$word\" and pos=\"$pos\" ");
			$sth->execute() or warn "$sth->errstr\n";;
		}
	}
}

#####for the nouns in @words, make the last n the main noun("-").
#####update NN's roles
#####return a positive number if an update is made,
sub updatenn{
	my($start,$end, @words) = @_;
	my ($update, $i, $sth1, $count);
	@words = splice(@words, $start, $end-$start);#all Ns
	for($i = 0; $i < @words-1; $i++){
		#one N at a time
    	$update += update($words[$i], "m", "", "modifiers", 1) if $words[$i] !~/\b($stop)\b/ and $words[$i] !~ /ly\s*$/ and $words[$i] !~ /\b($FORBIDDEN)\b/; #update modifier
	}
	#$update += update($words[$i], "n", "-"); #last one is the main noun
	#$update += update(join(" ",@words), "n","") if @words >=2;#NN altogether
	return $update;
}

sub addmodifier{
	my $m = shift;
	my ($sth1, $count, $update, $increment);
	return if $m=~/\b($stop|\w+ly)\b/ || $m !~/\w/;
	
	$sth1 = $dbh->prepare("select count from ".$dataprefix."_modifiers where word='$m'");
    $sth1->execute();
    $count = $sth1->fetchrow_array();
    if($count < 1){
       $sth1 = $dbh->prepare("insert into ".$dataprefix."_modifiers values ('$m', 1, 0)");
       $sth1->execute();
       $update = 1;
       print "new modifier $m added\n" if $debug;
    }else{
       $count += $increment; #6/11/09 from +1 to +increment
       $sth1 = $dbh->prepare("update ".$dataprefix."_modifiers set count = $count where word='$m'");
       $sth1->execute();
    }
    return $update;
}




####find the numerical value of a certainty
sub tovalue{
	my $certainty = shift;
	return -1 if (index($certainty, "/") != rindex($certainty,"/"));
	my ($u,$l) = split(/\//,$certainty);
	if ($l == 0){
	  return 1;
	 }
	return $u/$l;
}
########update %NOUNS and %BDRY; handle all updates, if a "p", also add its "s"
########save any NN in %NOUNS, note the role of Ns
########"1/1_" modifier:"_", main noun:"-", both: "+";
#####return 1 if an update is made, otherwise 0;
### use markknown to replace updatePOS subroutine 11/20/08 Hong
### update =>markknown =>processnewword =>updatePOS
sub update{
	my ($word, $pos, $role, $table, $increment) = @_; #("Base", "n", "_") or ("Base", "b", ""),
	my $new;
	#$word = lc $word;
	$word =~ s#<\S+?>##g; #remove tag from the word
	$word =~ s#\s+$##;
	$word =~ s#^\s*##;
	#if($word !~ /\w/ || $word =~/\b(?:$FORBIDDEN)\b/){return;}
	if(length($word) <1 or $word =~/\b(?:$FORBIDDEN)\b/){return;}
	
	if($pos eq "n"){
		$pos = getnumber($word);
	}
  
  $new += markknown($word, $pos, $role, $table, $increment);
  if(!insingularpluralpair($word)){#add this condition 3/12/09 to eliminate reduandent computation
	  if($pos eq "p"){
	  	my $pl = $word;
	    $word = singular($word);
	    $new += markknown($word, "s", "*", $table, 0);#6/11/09 add "*" and 0: pos for those words are inferred based on other clues, not seen directly from the text
	    addsingularpluralpair($word, $pl);
	  }
	  if($pos eq "s"){
	    my @words = plural($word);
	    my $sg = $word;
	    foreach my $w (@words){
	      $new += markknown($w, "p", "*", $table, 0) if $w =~/\w/;
	      addsingularpluralpair($sg, $w);
	    }
	  }
  }
  
  return $new;
}

sub insingularpluralpair{
	my $word = shift;
	my($sth);
	$sth = $dbh->prepare("select * from ".$dataprefix."_singularplural where singular = '$word' or plural ='$word' ");
	$sth->execute() or warn "$sth->errstr\n";
	if($sth->rows <= 0){
		return 0;
	}
	return 1;
}

sub addsingularpluralpair{
	my($sg, $pl) = @_;
	my($sth);
	$sth = $dbh->prepare("select * from ".$dataprefix."_singularplural where singular = '$sg' and plural ='$pl' ");
	$sth->execute() or warn "$sth->errstr\n";
	if($sth->rows <= 0){
		$sth = $dbh->prepare("insert into ".$dataprefix."_singularplural values ('$sg', '$pl')");
		$sth->execute() or warn "$sth->errstr\n";
	}
}

#p,s,b
#the three sets are exclusive of one another
sub updatePOS{
   my ($word, $pos, $role, $increment) = @_;
   my ($sth1, $sth, $new, $oldpos, $oldrole, $certaintyu, $certaintyl, $newwordflag);
   if($word =~ /(\b|_)(NUM|$NUMBERS|$CLUSTERSTRINGS|$CHARACTER)\b/ and $pos =~/[nsp]/){
   	return 0;
   }
	
	$word =~ s#(?<!\\)"#\\"#g;
	$newwordflag = 1;	
  	#updates should be in one transaction
	$sth1 = $dbh->prepare("select pos, role, certaintyu, certaintyl from ".$dataprefix."_wordpos where word='$word' ");
	$sth1->execute(); #return 1 record
	($oldpos, $oldrole, $certaintyu) = $sth1->fetchrow_array();
	if($oldpos !~ /\w/){#new word
		$certaintyu += $increment; #6/11/09 changed from = 1 to += $increment;
		$sth = $dbh->prepare("insert into ".$dataprefix."_wordpos values('$word','$pos', '$role',$certaintyu, 0)" );
	    $sth->execute();
		$new = 1;
		print "\t: new [$word] pos=$pos, role =$role, certaintyu=$certaintyu\n" if $debug;
	}elsif(($oldpos ne $pos) && ($oldpos eq "b" or $pos eq "b") ){ #different pos: b vs. s/p resolve conflicts, 
		my $otherpos = $pos ne "b" ? $pos : $oldpos; 
		$pos = resolveconflicts($word, "b", $otherpos);
		#$role = $pos eq $oldpos? $oldrole : $role; #6/11/09 remove
		if ($pos ne $oldpos){ #new pos win
			$role = $role eq "*" ? "" : $role;; #6/11/09 add
			$new += changePOS($word, $oldpos, $pos, $role, $increment) ;	
		}else{ #old pos win
			#$role = mergerole($oldrole, $role); #6/11/09
			$role = $oldrole eq "*" ? $role : $oldrole;
			$certaintyu += $increment; #change from +1 to +$increment
			$sth = $dbh->prepare("update ".$dataprefix."_wordpos set role ='$role', certaintyu =$certaintyu where word='$word' and pos='$pos' ");
    		$sth->execute();
			print "\t: update [$word($pos):a] role: $oldrole=>$role, certaintyu=$certaintyu\n" if $debug;
		}
	}else{#old and new pos are all [n],  update role and certaintyu
		$role = mergerole($oldrole, $role);
		$certaintyu += $increment; #change from +1 to +$increment
		$sth = $dbh->prepare("update ".$dataprefix."_wordpos set role ='$role', certaintyu =$certaintyu where word='$word' and pos='$pos' ");
    	$sth->execute();
		print "\t: update [$word($pos):b] role: $oldrole=>$role, certaintyu=$certaintyu\n" if $debug;
	}
	
	#update certaintyl = sum (certaintyu)
	$sth = $dbh->prepare("select sum(certaintyu) from ".$dataprefix."_wordpos where word=\"$word\"");
    $sth->execute();
	($certaintyl) = $sth->fetchrow_array();
	$sth = $dbh->prepare("update ".$dataprefix."_wordpos set certaintyl=$certaintyl where word=\"$word\"");
    $sth->execute();
	print "\t: total occurance of [$word] =$certaintyl\n" if $debug;
  return $new;
}

#if $word appears after a pl in the corpus, return bpos, otherwise, return the other
sub resolveconflicts{
	my ($word, $bpos, $otherpos) = @_;
	my ($sth, $sentence, $count);
	
	$sth = $dbh->prepare("select originalsent from ".$dataprefix."_sentence where (tag != 'ignore' or isnull(tag)) and originalsent rlike '[a-z]+($PLENDINGS) $word' ");
	$sth->execute() or warn "$sth->errstr\n";
	while(($sentence) = $sth->fetchrow_array()){
		if($sentence =~ /([a-z]+($PLENDINGS)) ($word)/i){
			my $pl = $1;
			$pl = lc $pl;
			if(getnumber($pl) eq 'p'){
				$count++;
			}
			if($count >= 1){
				return $bpos;
			}
		}
	}
	
	
	return $otherpos;	
}


#sub updatePOS{
#   my ($word, $pos, $role) = @_;
#   my ($sth1, $sth, $new, $oldrole, $certaintyu, $certaintyl);

	
#  	#updates should be in one transaction
#	$sth1 = $dbh->prepare("select role, certaintyu from ".$dataprefix."_wordpos where word=\"$word\" and pos=\"$pos\"");
#	$sth1->execute();
#	if($sth1->rows == 0){
#	    #new pos, insert new record
#		$certaintyu = 1;
#		$sth = $dbh->prepare("insert into ".$dataprefix."_wordpos values(\"$word\",\"$pos\", \"$role\",$certaintyu,0)");
#	    $sth->execute();
#		$new = 1;
#		print "\t: new [$word] pos=$pos, role =$role, certaintyu=$certaintyu\n" if $debug;
#	}else{
#	    #seens pos, update role and certaintyu
#	    ($oldrole, $certaintyu) = $sth1->fetchrow_array();
#		$role = mergerole($oldrole, $role);
#		$certaintyu++;
#		$sth = $dbh->prepare("update ".$dataprefix."_wordpos set role =\"$role\", certaintyu =$certaintyu where word=\"$word\" and pos=\"$pos\"");
#	    $sth->execute();
#		print "\t: update [$word($pos)] role: $oldrole=>$role, certaintyu=$certaintyu\n" if $debug;
#	}
#	#update certaintyl = sum (certaintyu)
#	$sth = $dbh->prepare("select sum(certaintyu) from ".$dataprefix."_wordpos where word=\"$word\"");
#   $sth->execute();
#	($certaintyl) = $sth->fetchrow_array();
#	$sth = $dbh->prepare("update ".$dataprefix."_wordpos set certaintyl=$certaintyl where word=\"$word\"");
#    $sth->execute();
#	print "\t: total occurance of [$word] =$certaintyl\n" if $debug;
#  return $new;
#}

#6/11/09 updated 
#a role may be "*"
sub mergerole{
	my($role1, $role2) = @_;
	
	if($role1 eq "*"){
		print "role * changed to $role2\n" if $debug;
	    return $role2;
	}elsif($role2 eq "*"){
		print "role * changed to $role1\n" if $debug;
	    return $role1;
	}
	
	if($role1 eq ""){
	    return $role2;
	}elsif($role2 eq ""){
	    return $role1;
	}elsif($role1 ne $role2){
		return "+"; 
	}else{
		return $role1;
	}
	#before 6/11/09
	#if($role1 eq ""){
	#    return $role2;
	#}elsif($role2 eq ""){
	#    return $role1;
	#}elsif($role1 ne $role2){
	#	return "+"; 
	#}else{
	#	return $role1;
	#}
}

#sub setpos{
#    #return 1 if new pos/role is set, otherwise return 0
#	my ($word, $pos, $certainty, $role) = @_;
#	my $new = 0;
#	my ($certaintyu, $certaintyl, $sth);
#	chomp($word);
#	$word =~ s#^\s*##;
#	$word = lc $word;
#	($certaintyu, $certaintyl) = split(/\//, $certainty);
#	if($word =~ /shade forms \) to/){
#	    print;
#	}
#	if($pos eq "n"){
#		$pos = getnumber($word); #update pos may be p,s, or n
#	}
#	#select into ".$dataprefix."_wordpos
#	$sth = $dbh->prepare("select count(*) from ".$dataprefix."_wordpos where word=\"$word\" and pos=\"$pos\"");
#	$sth->execute();
#	if($sth->fetchrow_array() == 0){
#	    #update
#		$sth = $dbh->prepare("insert into ".$dataprefix."_wordpos values(\"$word\",\"$pos\", \"$role\",\"$certaintyu\",\"$certaintyl\")");
#	    $sth->execute();
#		$new = 1;
#	}
#	return $new;
#}
####

#always call getnumber to get number, not checkWN($word, "number").
sub getnumber{
  my $word = shift;
  #$word = lc $word;
  my $number = checkWN($word, "number");
  return $number if $number =~/[sp]/;
  return "" if $number=~/x/;
  if($word =~/i$/) {return "p";} #1.	Calyculi  => 1.	Calyculus, pappi => pappus
  if ($word =~ /ss$/){return "s";}
  if($word =~/ia$/) {return "p";}
  if($word =~/[it]um$/) {return "s";}#3/13/09
  if ($word =~/ae$/){return "p";}
  if($word =~/ous$/){return ""; }
  if($word =~/^[aiu]s$/){return ""; }
  if ($word =~/us$/){return "s";}
  if($word =~ /es$/ || $word =~ /s$/){return "p";}
  if($word =~/ate$/){return "";} #3/12/09 good.
  return "s";
}

#####return pos string
sub checkpos{
	my ($word,$mode) = @_;#$mode = "one","all"
	my @posinfo = checkposinfo($word, $mode);
	if($mode eq "one"){
		return $posinfo[0][0];
	}else{
  		print "wrong mode in checkpos\n";
	}

}
sub printposinfo{
my @posinfo =@_;
my ($string, $i, $j);
for($i = 0; $i<@posinfo; $i++){
   for($j =0; $j <3; $j++){
     $string .= $posinfo[$i][$j]." ";
}
   $string .= "\n";
}
return $string;
}


########return a 2d array of (pos,role, certainty/certaintyl)
sub checkposinfo{
	my ($word,$mode) = @_;#$mode = "one","top", "all"
	#"one":the top one with top certainty, "all":all
	my ($pos, $role, $certaintyu, $certaintyl, $certainty, $maxcertainty);
	my ($sth, @results, $count, $stmt);
	#$word = lc $word;
	$word =~ s#\s+$##;
	$word =~ s#^\s*##;
	if($word =~ /^\d+/){
		my @temp = ("b", "", "1/1");
	    $results[0] = \@temp;
	    return @results;
	}
	#select pos from ".$dataprefix."_wordpos
	$stmt = "select pos, role, certaintyu, certaintyl from ".$dataprefix."_wordpos where word=\"$word\" order by certaintyu/certaintyl desc";
	$sth = $dbh->prepare($stmt);
	$sth->execute();
	if($sth->rows ==0){
	    my @temp = ("?", "?", "?");
	    $results[0] = \@temp;
	    return @results;
	}
 	$count = 0;
  #on sorted certainty ratio
  while(($pos, $role, $certaintyu, $certaintyl)=$sth->fetchrow_array()){
			my @temp = ();
			push(@temp, $pos, $role, $certaintyu."/".$certaintyl);
			$results[$count++] = \@temp;
	    return @results if $mode eq "one";
  }
	return @results; #if "all"
}

### find the one with the greatest $certaintyl (since the value of certainty is the same)
#sub chooseone{
#my @results = @_;
#my ($index, $max, $pos, $role, $certaintyu, $certaintyl, @result, $i);
#$max = 0;
#for($i = 0; $i < @results; $i++){
#  ($pos, $role, $certaintyu, $certaintyl) = ($results[$i][0],$results[$i][1],$results[$i][2],$results[$i][3]) ;
#  $max = $certaintyl if $max < $certaintyl;
#  $index = $i if $max < $certaintyl;
#}
#
#$result[0] = $results[$index];
#return @result;
#}

	#@todo
#if(defined $posn && !defined $posb){ #"s1/2_"
#	    if($posn =~ /([psn])(\d+\/\d+)([-_+]?)/){
#			return ($1,$2,$3); #("s", "1/2", "_")
#		}else{
#			print STDERR "POS in wrong format: $posn\n";
#		}
#	}elsif(defined $posb && !defined $posn){
#		return ("b", $posb,"");
#	}elsif(!defined $posn && !defined $posb){
#		return ("?","?","");
#	}else{#has conflict POSs, return both
#		return ("2", $posn."b".$posb, ""); #"s1/2_b1/2"
#	}



########return an array
sub getleadwords{
	my $sentid = shift;
	my ($sth,$lead);
	$sth = $dbh->prepare("select lead from ".$dataprefix."_sentence where sentid=$sentid");
	$sth->execute();
	$lead = $sth->fetchrow_array();
	return split(/\s+/,$lead);
}


########e.g. given $ptn = NN?,
########           $words = flower buds few,
########     return pattern "/\d+\{.*?\}flower buds \w+/i"
########should not rely on the length of $ptn or the number of words in $words
sub buildwordpattern{
	my ($words, $ptn) = @_;
	my $pattern = "\\d+\{.*?\}";
	my @w = split(/\s+/, $words);
        my $i;
	for($i = 0; $i < @w; $i++){
		if(substr($ptn,$i,1)eq "?"){
			$pattern .="\\w+ ";
		}else{
			$pattern .= $w[$i]." ";
		}
	}
	chop($pattern);
	return $pattern;
}
########return the pattern that matches any sentence
########whose first $N words match any word in @words that is not in $CHECKEDWORDS
########e.g. /^(cat|dogs|fish)|^\w+\s(cat|dogs|fish)|^\w+\s\w+\s(cat|dogs|fish)/
sub buildpattern{
	my @words = @_;
	my @newwords =();
	my ($pattern, $tmp, $i);
    my $prefix ="\\w+\\s";
	print ("CHECKEDWORDS is\n[$CHECKEDWORDS]\n") if $debug;
	#identify new words
	foreach (@words){
		if($_!~ /[[:punct:]0-9]/ && $CHECKEDWORDS !~ /:$_:/i){
			$tmp .= $_."|"; #(cat|dogs|fish)
			push(@newwords,$_);
		}
	}
	if($tmp !~ /\w/){return "";}#no new words
	#build pattern out of the new words
	chop($tmp);
	print ("Pattern segment: [$tmp]\n") if $debug;
	$tmp = "\\b(?:".$tmp.")\\b"; 
	$pattern ="^".$tmp."|";
	#$pattern =$tmp."|";
	for($i = 0; $i < $N-1; $i++){
		$tmp = $prefix.$tmp;
		$pattern .= "^".$tmp."|";
		#$pattern .=$tmp."|";
		#^(?:cat|dogs|fish)|^\w+\s(?:cat|dogs|fish)|^\w+\s\w+\s(?:cat|dogs|fish)
	}
	chop($pattern);
	$pattern = "(?:".$pattern.")";
	print ("Pattern: [$pattern]\n") if $debug;
	$CHECKEDWORDS .= join(":",@newwords).":";
	return $pattern;
}

######check wordnet to gether information about a word
######save checked words in hashtables
######find "singular": if a plural noun, return its singular form, otherwise, return itself
######find "number" if a noun, return "p" [plural] or "s"[singular]; if not in WN ""; otherwise "x"
######find "pos":return n [p,s], v, a, r, or "" (not in WN);
sub checkWN{
  my ($word, $mode) = @_;
  #$word = lc $word;
  #check saved records
  $word =~ s#\W##g; #remove non-word characters, such as <>
  return "" if $word eq "";
  my $singular = $WNSINGULAR{$word} if $mode eq "singular";
  return $singular if $singular =~ /\w/;
  my $number = $WNNUMBER{$word} if $mode eq "number";
  return $number if $number =~ /\w/;
  my $pos = $WNPOS{$word} if $mode eq "pos";
  return $pos if $pos =~/\w/;
  
  #special cases
  if ($word eq "teeth"){
    $WNNUMBER{"teeth"} = "p";
    $WNSINGULAR{"teeth"} = "tooth";
    return $mode eq "singular"? "tooth" : "p";
  }

  if ($word eq "tooth"){
    $WNNUMBER{"tooth"} = "s";
    $WNSINGULAR{"tooth"} = "tooth";
    return $mode eq "singular"? "tooth" : "s";
  }
  
  if ($word eq "NUM")
  {
    return $mode eq "singular"? "NUM" : "s";
  }
  
  if ($word eq "or"){
    return $mode eq "singular"? "or" : "";
   }
  
   if ($word eq "and"){
    return $mode eq "singular"? "and" : "";
  }
  
  if ($word =~ /[a-z]{3,}ly$/){#concentrically
    return $word if $mode eq "singular";
    return "" if $mode eq "number";
    return "r" if $mode eq "pos";
  }

  #otherwise, call wn
  my $result = `wn $word -over`;
  if ($result !~/\w/){#word not in WN
  	$WNPOSRECORDS{$word} = ""; #5/10/09
  	#return $mode eq "singular"? $word : ""; #not in WN, return ""
  	#remove any prefix and try again 3/12/09
  	my $wordcopy = $word;
  	$word =~ s#ed$##;
  	if($word ne $wordcopy){ #$word not end with "ed"
  		$result = `wn $word -over`;
  		if($result =~ /\w/){ #$word end with "ed", what remains after removes "ed" is still a word
  			return $word if $mode eq "singular";
  			return "" if $mode eq "number";
  			return "a" if $mode eq "pos";
  		}
  		
  	}
  	$word = $wordcopy;
  	$word =~ s#^($PREFIX)+##;
  	if($word eq $wordcopy){ 
  		return $mode eq "singular"? $word : ""; #not in WN, return ""
  	}else{
  		$result = `wn $word -over`;
  		$result =~ s#\b$word\b#$wordcopy#g;
  		$word = $wordcopy;
  		return $mode eq "singular"? $word : "" if ($result !~/\w/); 
  	}
  } 
  
  #found $word in WN:
  $result =~ s#\n# #g;
  if($mode eq "singular" || $mode eq "number"){
    my $t = "";
    while($result =~/Overview of noun (\w+) (.*) /){
         $t .= $1." ";
         $result = $2;
    }
    if ($t !~ /\w/){#$word is not a noun
    	#return "v";
    	return $mode eq "singular"? $word : "x"; #is not a noun, return "x"
    } 
    $t =~ s#\s+$##;
    my @ts = split(/\s+/, $t);
    ###select the singular between roots and root.   bases => basis and base?
    if(@ts > 1){
      my $l = 100;
      print "Word $word has singular\n" if $debug;
      foreach (@ts){
       print "$_\n" if $debug;
       # -um => a, -us => i?
       if (length $_ < $l){
          $t = $_;
          $l = length $_;
       }
      }
      print "The singular is $t\n" if $debug;
    }
    if ($t ne $word){
       $WNSINGULAR{$word} = $t;
       $WNNUMBER{$word} = "p";
       return $mode eq "singular"? $t : "p";
    }else{
       $WNSINGULAR{$word} = $t;
       $WNNUMBER{$word} = "s";
       return $mode eq "singular"? $t : "s";
    }
 }elsif($mode eq "pos"){
   my $pos = "";
   while($result =~/.*?Overview of ([a-z]*) (.*)/){
         my $t = $1;
         $result = $2;
         $pos .= "n" if $t eq "noun";
         $pos .= "v" if $t eq "verb";
         $pos .= "a" if $t eq "adj";
         $pos .= "r" if $t eq "adv";

    }
    $WNPOSRECORDS{$word}=$pos;
    if($pos =~/n/ && $pos =~/v/ && $word=~/(ed|ing)$/){ #appearing is a nv, but set it v
    	$pos =~ s#n##g;
    }
    $WNPOS{$word} = $pos;
    print "Wordnet Pos for $word is $pos\n" if $debug;
    return $pos;
  }
 }
 
 ##turn a singular word to its plural form
 ##check to make sure the plural form appeared in the text.
sub plural{
 my $word = shift;
 return "" if $word =~/^(n|2n|x)$/;
 my $plural = $PLURALS{$word};
 if ($plural=~/\w+/){
    my @pls = split(/ /, $plural);
    return @pls;
 }
 
 #special cases
 if($word =~ /series$/){
  $plural = $word;
 }elsif($word =~ /(.*?)foot$/){
  $plural = $1."feet";
 }elsif($word =~ /(.*?)tooth$/){
  $plural = $1."teeth";
 }elsif($word =~ /(.*?)alga$/){
  $plural = $1."algae";
 }elsif($word =~ /(.*?)genus$/){
  $plural = $1."genera";
 }elsif($word =~ /(.*?)corpus$/){
  $plural = $1."corpora";
 }else{
    #rules
    if($word =~ /(.*?)(ex|ix)$/){
      $plural = $1."ices"; #apex=>apices
      $plural .= " ".$1.$2."es";
    }elsif($word =~ /(x|ch|ss|sh)$/){
      $plural = $word."es";
    }elsif($word =~ /(.*?)([^aeiouy])y$/){
      $plural = $1.$2."ies";
    }elsif($word =~ /(.*?)(?:([^f])fe�([oaelr])f)$/){
      $plural = $1.$2.$3."ves";
    }elsif($word =~ /(.*?)(x|s)is$/){
      $plural = $1.$2."es";
    }elsif($word =~ /(.*?)([tidlv])um$/){
      $plural = $1.$2."a";
    }elsif($word =~ /(.*?)(ex|ix)$/){
      $plural = $1."ices"; #apex=>apices
    }elsif($word =~ /(.*?[^t][^i])on$/){
      $plural = $1."a"; #taxon => taxa but not venation =>venatia
    }elsif($word =~/(.*?)a$/){
      $plural = $1."ae";
    }elsif($word =~ /(.*?)man$/){
      $plural = $1."men";
    }elsif($word =~ /(.*?)child$/){
      $plural = $1."children";
    }elsif($word =~ /(.*)status$/){
      $plural = $1."statuses";
    }elsif($word =~ /(.+?)us$/){
      $plural = $1."i";
      $plural .= " ".$1."uses";
    }elsif($word =~/s$/){
      $plural = $word."es";
    }
    $plural .= " ".$word."s"; #another choice
 }
  print "$word plural is $plural\n" if $debug;
   
  $plural =~ s#^\s+##;
  $plural =~ s#\s+$##;
  my @pls = split(/ /, $plural);
  my $plstring = "";
  foreach my $p (@pls){
    if ($WORDS{$p} >= 1){
      $plstring .=$p." ";
    }
  }
  $plstring =~ s#\s+$##;
  $PLURALS{$word} = $plstring;
  print "confirmed $word plural is *$plstring*\n" if $plstring=~/\w/ && $debug;
  @pls = split(/ /, $plstring);
  return @pls;

}

########read $dir, mark sentences with $SENTMARKER,
########put space around puncts, save sentences in %SENTS,
########"sentences" here include . or ; ending text blocks.
######## put all unique words in unknownwords
sub populatesents{
	my %paragraphs = @_;
	
my ($text, @sentences,@words,@tmp,$status,$lead,$stmt,$sth, $escaped, $original, $count);
	
foreach my $pid (keys(%paragraphs)){
	$text = $paragraphs{$pid};	
	$text =~ s#\s*-\s*to\s+# to #g; #4/7/09 plano - to
	$text =~ s#[-\s]+shaped#-shaped#g; #5/30/09
	$text =~ s#<.*?>##g; #remove html tags
	$text =~ s#<# less than #g;
	$text =~ s#># greater than #g;
	$original = $text;
  	$text =~ s/&[;#\w\d]+;/ /g; #remove HTML entities
  	$text =~ s#\([^()]*?[a-zA-Z][^()]*?\)# #g;  #remove (.a.)
  	$text =~ s#\[[^\]\[]*?[a-zA-Z][^\]\[]*?\]# #g;  #remove [.a.]
  	$text =~ s#{[^{}]*?[a-zA-Z][^{}]*?}# #g; #remove {.a.}
  	$text =~ s#_#-#g;   #_ to -
  	$text =~ s#\s+([:;\.])#$1#g;     #absent ; => absent;
  	$text =~ s#(\w)([:;\.])(\w)#$1$2 $3#g; #absent;blade => absent; blade
  	$text =~ s#(\d\s*\.)\s+(\d)#$1$2#g; #1 . 5 => 1.5
  	$text =~ s#(\sdiam)\s+(\.)#$1$2#g; #diam . =>diam.
  	$text =~ s#(\sca)\s+(\.)#$1$2#g;  # ca . =>ca.
	#$text = hideBrackets($text);#@todo: avoid splits in brackets.  this is how. Not doing anything because all paird brackets have been remove above.
  	$text =~ s#(\d\s+(cm|mm|dm|m)\s*)\.(\s+[^A-Z])#$1\[DOT\]$3#g;
	#@todo: use [PERIOD] replace . etc. in brackets. Replace back when dump to disk.
	
	@sentences = SentenceSpliter::get_sentences($text);#@todo: avoid splits in brackets. how?
	
 	foreach (@sentences){
		#may have fewer than $N words
		if(!/\w+/){next;}
		s#\[DOT\]#.#g;

    	#s#([^\d])\s*-\s*([^\d])#\1_\2#g;         #hyphened words: - =>_ to avoid space padding in the next step
		s#\s*[-]+\s*([a-z])#_\1#g;                #cup_shaped, 3_nerved, 3-5 (-7)_nerved #5/30/09 add+
		s#(\W)# \1 #g;                            #add space around nonword char 
    	#s#& (\w{1,5}) ;#&\1;#g;          
    	s#\s+# #g;                                #multiple spaces => 1 space 
    	s#^\s*##;                                 #trim
    	s#\s*$##;                                 #trim 
    	#recordpropernouns($_);
    	tr/A-Z/a-z/;                              #all to lower case
    	getallwords($_);
  	}	

	$count = 0;
 	foreach (@sentences){
		#may have fewer than $N words
		if(!/\w+/){next;}
		my $line = $_;
		my $oline = getOriginal($line, $original, $pid);
    	
    	$line =~ s#'# #g; #remove all ' to avoid escape problems
    	$oline =~ s#'# #g;
    	@words = getfirstnwords($line, $N); # "w1 w2 w3"
    
    	$status = "";
		if(getnumber($words[0]) eq "p"){
		     $status = "start";
		}else{
		     $status = "normal";
		}
		$lead = "@words";
		$lead =~ s#\s+$##;
		$lead =~ s#^\s*##;
		$lead =~ s#\s+# #g;
		#s#"#\\"#g;
    	#s#'#\\'#g;
    	
    	#s#\(#\\(#g;
    	#s#\)#\\)#g;
    	my $source = $pid."-".$count++;
    	if(length($oline) >=2000 ){#EOL
    		$oline = $line;
    	}
    	
    	$line =~ s#^(a|an|the|\W+)\b\s*##i;
    	$stmt = "insert into ".$dataprefix."_sentence(sentid, source, sentence, originalsent, lead, status) values($SENTID,'$source' ,'$line','$oline','$lead', '$status')";
		$sth = $dbh->prepare($stmt);
    	$sth->execute() or die $sth->errstr."\n SQL Statement: ".$stmt."\n";
		#print "Sentence: $line\n" if $debug;
		#print "Leading words: @words\n\n" if $debug;
		$SENTID++;
	}
	my $end = $SENTID-1;
	$NEWDESCRIPTION.=$pid."[".$end."] ";
	my $query = $dbh->prepare("insert into ".$dataprefix."_sentInFile values ('$pid', $end)");
	$query->execute() or warn $query->errstr."\n";
}
	chop($PROPERNOUNS);
print "Total sentences = $SENTID\n";
populateunknownwordstable();
}

sub hideBrackets{
	my $text = shift;
	my $hidden = "";
	while($text=~/(.*?)(\([^()]*?[a-zA-Z][^()]*?\))(.*)/){
		my $p1 = $1;
		my $p2 = $2;
		$text = $3;
		$p2 =~ s#\.#\[DOT\]#g;
		$hidden .= $p1.$p2; 
	}
	$hidden .=$text;
	$text = $hidden;
	$hidden = "";
	while($text=~/(.*?)(\[[^\[\]]*?[a-zA-Z][^\[\]]*?\])(.*)/){
		my $p1 = $1;
		my $p2 = $2;
		$text = $3;
		$p2 =~ s#\.#\[DOT\]#g;
		$hidden .= $p1.$p2; 
	}
	$hidden .=$text;
	$text = $hidden;
	$hidden = "";
	while($text=~/(.*?)({[^{}]*?[a-zA-Z][^{}]*?})(.*)/){
		my $p1 = $1;
		my $p2 = $2;
		$text = $3;
		$p2 =~ s#\.#\[DOT\]#g;
		$hidden .= $p1.$p2; 
	}
	$hidden .=$text;
	
	return $hidden;
}
sub recordpropernouns{
	my $sent = shift;
	$sent =~ s#[(\[{]\s*[A-Z]# #g;#hide *[ D*iagnosis prepared by ...]
	while($sent =~/(.+)\b([A-Z][a-z]*)\b/){
		my $pn = $2;
		print "find a pn [$pn] in [$sent]\n" if $debug;
		$sent = $1;
		$pn =~ tr/A-Z/a-z/;
		$PROPERNOUNS .= $pn."|" if length($pn) > 1;
	}	
}

sub getallwords{
  my $sentence = shift;
  my @words = tokenize($sentence, "all");
  foreach my $w (@words){
    $WORDS{$w}++;
  }
}

sub populateunknownwordstable{
	my $count = 0;
	foreach my $word (keys(%WORDS)){
		#if(($word !~ /\w/)||($word =~ /_/ && $word !~/\b($FORBIDDEN)\b/ && $word !~ /\b($stop)\b/)){ #pistillate_zone
		if($word !~ /\w/ || $word=~/ous$/){
			$word = "\\".$word if $word eq "'";
			print $word."\n";
			my $sth = $dbh->prepare("insert into ".$dataprefix."_unknownwords values ('$word', '$word')");
			$sth->execute() or warn $sth->errstr."\n";
			update($word, "b", "", "wordpos", 1);
		}else{
			my $sth = $dbh->prepare("insert into ".$dataprefix."_unknownwords values ('$word', 'unknown')");
			$sth->execute() or warn $sth->errstr."\n";
		}
		$count++;
	}
	print "Total words = $count\n";
}

sub getfirstnwords{
	########return the first up to $n words of $sentence as an array, excluding
	my($sentence, $n) = @_;
	$sentence =~ s#^(a|an|the|\W+)\b\s*##i;
	my (@words, $index, $w);
	@words = tokenize($sentence, "firstseg");
	#print "words in sentence: @words\n" if $debug;
	@words = splice(@words, 0, $n);
	return @words;
}

#extract the segment matching $line from $original, mainly to get original case and parentheses
#$line: pappi , 20 � 40 mm , usually noticeably shorter than corolla .
#$orginal:... Pappi (white or tawny), 20�40mm, usually noticeably shorter than corolla. ...
#Pollen 70�100% 3-porate, mean 25 �m
sub getOriginal{
	my ($line, $original, $file) = @_;
	my $pattern1 = $line; 

	if($line=~/[)(]/ && $line !~/\(.*?\)/){
		print "====>Unmatched paranthesis in $file: $line\n\n";
	}	

	$pattern1 =~ s#([)(\[\]}{.+?*])#\\\1#g; #escape )([]{}.+*?
		
	$pattern1 =~ s/([-_])/ [-_]+ \\s*/g; #deal with _-, leave a space here for later conversion
	
	$pattern1 =~s#[^-()\[\]{}<>_!`~\#$%^&/\\.,*;:0-9a-zA-Z?="'+@ ]#.#g; #replace non-word non-punc char with a .
	
	$pattern1 =~ s/&#\d+;/./g; #replace HTML entities with a .
	#$text =~ s/&[;#\w\d]+;/ /g;
	$pattern1 =~ s/\s+/\\s*(([\\(\\[\\{].*?[\\}\\]\\)])|(&[;#\\w\\d]+;))*\\s*/g;         #all spaces => \s*(\(.*?\))?\s* or html entities

	if($original =~ /($pattern1)/i){
		my $oline = $1;
		return $oline;
	}else{
		print "\nline ====> $line\n\n";
		print "orginal ====> $original\n";
		print "pattern ====> $pattern1\n";
		die "the above doesn't match\n\n"; #TODO: die or warn
	}
}

#mode: "all" , "firstseg"
sub tokenize{
    my ($sentence, $mode) = @_;
	my ($index, @words, $temp1, $temp2);
    if($mode ne "all"){
    	$temp1 = length($sentence); #3/11/09
    	$temp2 = $temp1;#
    	if($sentence =~ / [\[\](){},:;\.]/){ #6/20/10: need to stop at a bracket because only matches brackets are removed in the eariler process
			$temp1 = $-[0];
			#$index = $temp1;#
		}
		if($sentence =~ /\b(?:$PROPOSITION)(\s)/){#3/1109
			$temp2 = $-[1];
		}
		$index = $temp1 < $temp2? $temp1 : $temp2;#
	}else{
		$index = length($sentence);
	}
	$sentence = substr($sentence, 0, $index);

	#$sentence =~ s#[[:punct:]]# #g; #remove all punct. marks
	#$sentence =~ s#\W# #g; #keep punctuation marks in $lead
	$sentence =~ s#(\(? ?\d[\d\W]*)# NUM #g; 
	$sentence =~ s#\b($NUMBERS|_)+\b# NUM #g; #3/12/09
	$sentence =~ s#\s+$##;
	$sentence =~ s#^\s+##;
	@words = split(/\s+/, $sentence);
  	return @words;
}

1;

