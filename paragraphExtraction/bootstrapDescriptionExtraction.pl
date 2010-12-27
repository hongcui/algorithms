#!/usr/bin/perl


# add behind to stoplist
# remove leading a/an/the/\W+ from sentence and lead
# species singluar is still species



#This program identifies morphological descriptions from heterogenous source documents (Plain_text), 
#using an unsupervised bootstrapping procedure
#
#It assumes 
#a) paragraphs from source documents are saved in a database table
#b) a set of seed paragraphs have been identified, and the unsupervisedOrganNameExtraction.pl has been 
#   run on the seeds, i.e. a set of seed organs can be obtained from the sentence table

#The bootstrapping occurs between organ names and morphological descriptions

use strict;
use DBI; #load module
use lib '..\\paragraphExtraction\\UnsupervisedClauseMarkup\\';
#use lib 'C:\\Docume~1\\hongcui\\Desktop\\WordNov2009\\Description_Extraction\\paragraphExtraction\\';
use SentenceSpliter;
use SeedDescriptionExtraction;
use unsupervisedOrganNameExtraction;

################################################################################################################
#note to Alyssa:
#how to run the program:
#1. restore database dump in your mySQL. You will find three datasets, each has two tables: 
#    one paragraphs table which is used by Perl, one benchmark table you used to calculate precision and recall.
#2. In your windows commandline, navigate to the directory you save the perl program. Run the perl program.
#	You will run perl program on each dataset. Using bhl dataset for an example, you run perl by typing the following on commandline:
#
#	perl bootstrapDescriptionExtraction.pl d bhl_paragraphs paragraphbootstrappingevaluation plain Z:\seeds prefix IEF1 LST IEF2 LCT ODT
#	bhl_benchmark bhl_paragraphs
#
#
#	You need to replace prefix, IEF1, LST, IEF2, LCT, and ODT with sensible values:
#	IEF1 ~ ODT are the five parameters, for example, you can have 0.3 10 0.5 20 0.5
#	Prefix will be used by the perl program to name various table produced. You need to save results from each run, therefore I guess you compose a prefix from the five parameters, 
#	for example, if you run the perl program using the above five parameters your prefix should be something like "bhl_03_10_05_20_05". 
#	Using above prefix and parameters, your command will look like
#	
#	perl bootstrapDescriptionExtraction.pl d bhl_paragraphs paragraphbootstrappingevaluation plain Z:\seeds bhl_03_10_05_20_05 0.3 10 0.5 20 0.5
#	bhl_benchmark bhl_paragraphs
#	
#	When run plaziants dataset, you run this command:
#
#	perl bootstrapDescriptionExtraction.pl d plaziants_paragraphs paragraphbootstrappingevaluation plain Z:\seeds plaziants_03_10_05_20_05 0.3 10 0.5 20 0.5
#	plaziants_benchmark plaziants_paragraphs
#
#	
################################################################################################################

#old instructions
#perl ..\paragraphExtraction\bootstrapDescriptionExtraction.pl f Z:\DATA\BHL\cleaned markedupdatasets plain Z:\DATA\BHL\target\seeds bhl_2vs (can not run evaluation due to mismatched paraID formats)
if(@ARGV != 13) { #changed from 5 to 6 on 6/10/2010 #added thresholds
print "\nbootstrapDescriptionExtraction.pl identifies morphological descriptions from heterogeneous source documents (Plain_text) \n";
print "the number of arguments was wrong\n";
exit(1);
}

# print stdout "Initialized:\n";
# ##################################################################
# #########################                                  #######
# #########################        set up global variables   ####### 
####################################################################

my $sourcetype = $ARGV[0];
my $source = $ARGV[1];
my $db = $ARGV[2];
my $mode = $ARGV[3];
my $seedfile =$ARGV[4];
my $prefix = $ARGV[5];

my $debug = 0;

	#########################################
	# configurable parameters:
	#########################################

my $itfactor1 = $ARGV[6];
my $sentcountthresh = $ARGV[7];
my $itfactor2 = $ARGV[8];
my $charcountthresh = $ARGV[9];
my $organdensitythresh = $ARGV[10];
my $benchmark = $ARGV[11];
my $mothertable = $ARGV[12];

my $lbdry = "<@<";
my $rbdry = ">@>";
#my $source = "C:\\Documents and Settings\\hongcui\\Desktop\\WordNov2009\\Description_Extraction\\extractionSource\\Plain_text";
#my $dir_to_save = "C:\\Documents and Settings\\hongcui\\Desktop\\WordNov2009\\Description_Extraction\\extractionData\\Description_paragraphs";
my $false_organs="ignore|\[parenttag\]|general|ditto";
my  @organ_names = ();

##############################################
#  connect to database
##############################################
my $host = "localhost";
my $user = "termsuser";
my $password = "termspassword";
my $dbh = DBI->connect("DBI:mysql:host=$host", $user, $password)
or die DBI->errstr."\n";

my $test = $dbh->prepare('create database if not exists '.$db.' CHARACTER SET utf8') or die $dbh->errstr."\n";
$test->execute() or die $test->errstr."\n";

$test = $dbh->prepare('use '.$db) or die $dbh->errstr."\n";
$test->execute() or die $test->errstr."\n";

###############################################
# create tables etc.
###############################################
#create paragraph tables (add a column for each iteration)
if($sourcetype eq "f"){
	my $del = $dbh->prepare('drop table if exists '.$prefix.'_paragraphs');
	$del->execute();
	my $create = $dbh->prepare('create table if not exists '.$prefix.'_paragraphs (paraID varchar(100) not null unique, paragraph text(20000), remark text(20000), flag varchar(10), primary key (paraID))');
	$create->execute() or warn "$create->errstr\n";
	populateParagraphTable(); #NOTE: MAKE SURE _benchmark database have the same kind of paraIDs defined !!!
	my $update = $dbh->prepare('update '.$prefix.'_paragraphs set remark=paragraph');
	$update->execute();
	$update = $dbh->prepare('update '.$prefix.'_paragraphs set flag = ""');
	$update->execute();
}elsif($sourcetype eq "d"){
	#if($source!~/^$prefix/){
	#	die "source paragraph table does not have the right prefix: $prefix\n";
	#}else{
		my $del = $dbh->prepare('drop table if exists '.$prefix.'_paragraphs');
		$del->execute();
		my $copy = $dbh->prepare('create table '.$prefix.'_paragraphs select * from '.$mothertable); #assumes the source table be bhl_clean_paragraphs
		$copy->execute() or warn "$copy->errstr\n";
		my $select = $dbh->prepare('select count(paraID) from '.$prefix.'_paragraphs');
		$select->execute() or warn "$select->errstr\n";
		my ($count) = $select->fetchrow_array();
		if($count<1){
			die "source paragraph table is empty\n";
		}
		my $update = $dbh->prepare('update '.$prefix.'_paragraphs set remark=paragraph');
		$update->execute();
		$update = $dbh->prepare('update '.$prefix.'_paragraphs set flag = ""');
		$update->execute();
	#}
}else{
	die "unknown sourcetype. Use f or d.\n";
}

#create paragraph bootstrap tabe
my $del = $dbh->prepare('drop table if exists '.$prefix.'_paragraphbootstrap');
$del->execute();
my $create = $dbh->prepare('create table if not exists '.$prefix.'_paragraphbootstrap (paraID varchar(100) not null unique,iter0 char(1), primary key (paraID))');
$create->execute() or warn "$create->errstr\n";

#create organ name table
$del = $dbh->prepare('drop table if exists '.$prefix.'_organnamebootstrap');
$del->execute();
$create = $dbh->prepare('create table if not exists '.$prefix.'_organnamebootstrap (organname varchar(100) not null unique,iter0 char(1), primary key (organname))');
$create->execute() or warn "$create->errstr\n";

#create performance table
my $create = $dbh->prepare('create table if not exists paragraph_extraction_evaluation (timestmp timestamp DEFAULT CURRENT_TIMESTAMP, dataset varchar(500), setting varchar(100), precison float(5,3), recall float(5,3), runtime int, primary key (timestmp))');
$create->execute() or warn "$create->errstr\n";

###########################################################
## load or learning seeds                                      
###########################################################
#seeds
#identifies seed paragraphs (set in iternation0 )
my @seeds = ();
if(-e $seedfile){
	open(S, "$seedfile") || warn "$seedfile: $!\n";
	while(my $l = <S>){
		if($l=~/\w/){
			push(@seeds, $l);
		}
	}
}
if(@seeds == 0){
@seeds = SeedDescriptionExtraction::collectSeedParagraphs($db, "paragraphs", $prefix); #find paragraphs not containing articles, pronouns, or helping verbs.
}
foreach (@seeds){
	my $insert = $dbh->prepare('insert into '.$prefix.'_paragraphbootstrap values("'.$_.'", 0)');
	$insert->execute() or warn "$insert->errstr\n";
}
##########################################################
## learning organs from seeds
##########################################################
#identifies seed organs
print "run unsupervisedOrganExtraction.pl for iteration 0\n";
my %paragraphs = collectParagraphs4CurrentIteration();
unsupervisedOrganNameExtraction::extractOrganNames($db, $mode, $prefix, %paragraphs); #find organ names from seed paragraph.

@organ_names = collectOrganNames();
foreach (@organ_names){
	my $insert = $dbh->prepare('insert into '.$prefix.'_organnamebootstrap values("'.$_.'", 0)');
	$insert->execute() or warn "$insert->errstr\n";
}

####################################################
## bootstrapping
####################################################
my ($sec, $min, $hr, @timeData) = localtime(time);
my $time1 = $hr*3600+$min*60+$sec; 

my $new = 0;
my $iteration = 0;
do{
	$iteration++;
	#add a column in organName and paragraph table
	my $alter = $dbh->prepare('alter table '.$prefix.'_paragraphbootstrap add '."iter".$iteration.' int');
	$alter->execute() or warn "$alter->errstr\n";
	my $alter = $dbh->prepare('alter table '.$prefix.'_organnamebootstrap add '."iter".$iteration.' int');
	$alter->execute() or warn "$alter->errstr\n";
	$new = bootstrap($iteration);
	
}while $new > 0;

lastround($iteration+1);



my ($sec, $min, $hr, @timeData) = localtime(time);
my $time2 = $hr*3600+$min*60+$sec;
my $runtime = $time2-$time1;
print "completed in $runtime seconds\n";

#####################################################
# performance evaluation
#####################################################
#my $itfactor1 = 1/3;
#my $sentcountthresh = 5;
#my $itfactor2 = 1/3;
#my $charcountthresh = 15;
#my $organdensitythresh = 0.5;

#assume $prefix_paragraphs_benchmark holds the answers

#bhl_clean_paragraphs_benchmark holds the answers

my $select = $dbh->prepare('show tables');
$select->execute() or warn '$select->errstr\n';
my $table = "";
my $exists = 0;
while (($table) = $select->fetchrow_array()){
	if($table eq $benchmark){
		$exists = 1;
	}
}
if($exists){
	my $all = 0;
	my $got = 0;
	my $got_good = 0;
	#my $select = $dbh->prepare('select count(paraID) from '.$prefix.'_paragraphs_benchmark where isDescription="y"');
	my $select = $dbh->prepare('select count(paraID) from '.$benchmark.' where isDescription="y"');
	$select->execute() or warn '$select->errstr\n';
	($all) = $select->fetchrow_array();
	$select = $dbh->prepare('select count(paraID) from '.$prefix.'_paragraphbootstrap');
	$select->execute() or warn '$select->errstr\n';
	($got) = $select->fetchrow_array();
	#$select = $dbh->prepare('select count(paraID) from '.$prefix.'_paragraphbootstrap where paraID in (select paraID from '.$prefix.'_paragraphs_benchmark where isDescription="y")');
	$select = $dbh->prepare('select count(paraID) from '.$prefix.'_paragraphbootstrap where paraID in (select paraID from '.$benchmark.' where isDescription="y")');
	$select->execute() or warn '$select->errstr\n';
	($got_good) = $select->fetchrow_array();
	#timestamp timestampe not null unique, dataset varchar(500), setting varchar(100), precision float, recall float, primary key (timestamp))
	my $precision = $got_good/$got;
	my $recall = $got_good/$all;
	my $q = 'insert into paragraph_extraction_evaluation (dataset, setting, precison, recall, runtime) values("'.$prefix.'","'.$itfactor1.'/'.$sentcountthresh.'/'.$itfactor2.'/'.$charcountthresh.'/'.$organdensitythresh.'", '.$precision.', '.$recall.', '.$runtime.')';
	my $insert = $dbh->prepare($q);
	$insert->execute() or warn '$insert->errstr\n';
	print "performance evaluation using precision [$precision] and recall[$recall]\n";
	# print "##########################false positives: \n";
	# $select = $dbh->prepare('select paraID, paragraph from '.$prefix.'_paragraphs_benchmark where isDescription !="y" and paraID in (select paraID from '.$prefix.'_paragraphbootstrap)');
	# $select->execute() or warn '$select->errstr\n';
	# while(my ($pid, $para) = $select->fetchrow_array()){
		# print "[$pid]$para\n";
	# }

	# print "##########################false negatives: \n";
	# $select = $dbh->prepare('select paraID, paragraph from '.$prefix.'_paragraphs_benchmark where isDescription ="y" and paraID not in (select paraID from '.$prefix.'_paragraphbootstrap)');
	# $select->execute() or warn '$select->errstr\n';
	# while(my ($pid, $para) = $select->fetchrow_array()){
		# print "[$pid]$para\n";
	# }

}else{
	print "no evaluation was performed because $benchmark table was not found\n" if $debug;
}
#####################################################
#                   sub-routines
#####################################################
#check the paragarphs that didn't meet the basic criteria (used in  MarkDescription sub)
sub lastround{
	my $iteration = shift;
	#prepare the tables for the last iteration
	my $round = $iteration -1;
	my $alter = $dbh->prepare('alter table '.$prefix.'_paragraphbootstrap add '."iter".$iteration.' int');
	$alter->execute() or warn "$alter->errstr\n";
	my $alter = $dbh->prepare('alter table '.$prefix.'_organnamebootstrap add '."iter".$iteration.' int');
	$alter->execute() or warn "$alter->errstr\n";
	my $update = $dbh->prepare('update '.$prefix.'_paragraphbootstrap set iter'.$iteration.'=iter'.$round);
	$update->execute() or warn "$update->errstr\n";
	
	#select candidates to process
	my $select = $dbh->prepare('select paraID, paragraph from '.$prefix.'_paragraphs where flag = "not"');
	$select->execute() or warn "$select->errstr\n";
	my @tbds = ();
	while (my ($pid) = $select->fetchrow_array()){
		push(@tbds, $pid); #to-be-determineds
	}
	
	my @organnames = selectOrganNames("iter".$round);
	@organnames = getAlsoPlurals(@organnames);
	my $organnames = join("|", @organnames);
	$organnames =~ s#\|+#|#g;
	$organnames =~ s#^\|##g;
	$organnames =~ s#\|$##g;
	my @domainterms = selectDomainTerms();
	my $domainterms = join("|", @domainterms);
	#$domainterms = $organnames."|".$domainterms;
	$domainterms =~ s#\|+#|#g;
	$domainterms =~ s#^\|##g;
	$domainterms =~ s#\|$##g;

	my %newp = ();
	my %notp = ();
	my %ps = ();
	foreach (@tbds){
		$select = $dbh->prepare('select paragraph from '.$prefix.'_paragraphs where paraID ="'.$_.'"');
		$select->execute() or warn "$select->errstr\n";
		my ($p) = $select->fetchrow_array();
		if($p !~/[a-z]/){next;} #HEADINGS Hong 6/24/2010
		$p =~ s#\bthe\b##ig;
		$p =~ s#^\s*\d+[a-z]?\W+([A-Z])#$1#;
		$p =~ s#^\W+##g;
		$p =~ s#(^\s+|\s+$)##g; #trim
		$p =~ s#[0-9_-]##g;
		$p =~ s#\b($organnames)\b#<$1>#gi;
		$p =~ s#\b($domainterms)\b#{$1}#gi;
		$ps{$_} = $p;
		my $v = domainTermDensity($p);
		if( $v > 0.33){ 
			$newp{$_} = $p;	
			print "YES Description[$v]: $p\n" if $debug;
		}elsif($p=~/^</){ 
			$newp{$_} = $p;
			print "YES Description[lead]: $p\n" if $debug;	
		}else{
			if($p =~ /^(.*?<[^<]*?>)/){
				my $l = $1;
				$notp{$l} .= $_." ";
				print "Not Sure Description: $p\n" if $debug;
			}
			print "Not Description[0]: $p\n" if $debug;
		}
	}
	my $stop = $unsupervisedOrganNameExtraction::PROPOSITION;
	$stop .= "|".$unsupervisedOrganNameExtraction::stop;
	$stop =~ s#\|+#|#g;
	$stop =~ s#^\|##g;
	$stop =~ s#\|$##g;
	#recover from %notp
	foreach (keys(%newp)){
		my $p = $newp{$_};
		if($p =~/^(.*?<[^<]*?>)/){
			my $lead = $1;
			if($lead !~ /\b($stop)\b/){
				my $list = $notp{$lead};
				$list =~ s#\s+$##;
				my @indexes = split(/\s+/, $list);
				foreach my $i (@indexes){
					$newp{$i} = $ps{$i};
					print $ps{$i}." is recovered as a description\n" if $debug;
				}
			}
		}
	}
	# my @organnames = selectOrganNames("iter".$round);
	# @organnames = getAlsoPlurals(@organnames);
	# my $organnames = join("|", @organnames);
	# $organnames =~ s#\|+#|#g;
	# $organnames =~ s#^\|##g;
	# $organnames =~ s#\|$##g;

	# my %newp = ();
	# foreach (@tbds){
		# $select = $dbh->prepare('select paragraph from '.$prefix.'_paragraphs where paraID ="'.$_.'"');
		# $select->execute() or warn "$select->errstr\n";
		# my ($p) = $select->fetchrow_array();
		# $p =~ s#(^\s+|\s+$)##g;
		# $p =~ s#\b($organnames)\b#<$1>#gi;
		# if($p=~/(^|\d[a-z]?[,\.]?\s*)<[A-Z]/){ #pick out "1a, Leaves"
			# $newp{$_} = $p;	
		# }
	# }
	updateParagraphBootstrap($iteration, %newp);
}

#Hong added, 6/14/10
sub domainTermDensity{
	my $p = shift;
	$p =~ s#^\s+##g;
	$p =~ s#\s+$##g;
	$p =~ s#{#<#g;
	$p =~ s#}#>#g;
	# $count = () = $string =~ /-\d+/g;
	my $wcount = () = $p =~ /\b\w+\b/g;
	my $dcount = () = $p =~ /<[^<]*?>/g;
	my $ccount = () = $p =~ /<[A-Z].*?>/g;
	my $v = 0;
	$v = ($dcount+$ccount)/$wcount if $wcount != 0;
	return $v;
}

#######################################################################################
#######################################################################################
###
### main bootstrap
########################################################################################
sub bootstrap{
	my $iteration = shift;
	my $new = 0;
	
	#collect organ name from $iteration-1
	my $round = $iteration-1;
	my @organnames = selectOrganNames("iter".$round);
	@organnames = getAlsoPlurals(@organnames);
	my $organnames = join("|", @organnames);
	$organnames =~ s#\|+#|#g;
	$organnames =~ s#^\|##g;
	$organnames =~ s#\|$##g;

	
	#use organ name to find morph. dscrpt prgrph, update prgrph bootstrap table for $iteration
	#assumption: no trace-back is done
	my $update = $dbh->prepare('update '.$prefix.'_paragraphbootstrap set iter'.$iteration.'=iter'.$round);
	$update->execute() or warn "$update->errstr\n";
	identifyParagraph($iteration, $organnames);
	###TODO
	
	#run unsupervise.pl to learn more organ names
	print "run unsupervisedOrganExtraction.pl for iteration ".$iteration;
	my %paragraphs = collectParagraphs4CurrentIteration();
	unsupervisedOrganNameExtraction::extractOrganNames($db, $mode, $prefix, %paragraphs);
	@organ_names = collectOrganNames();
	#update organ name bootstrap table
	$new = updateOrganNameBootstrapTable($iteration, @organ_names);
}

sub getAlsoPlurals{
	my @organnames = @_;
	my @all  = @organnames;
	foreach (@organnames){
		my $select = $dbh->prepare('select plural from '.$prefix.'_singularplural where singular ="'.$_.'"');
		$select->execute() or warn "$select->errstr\n";
		my ($plural) = $select->fetchrow_array();
		push(@all, $plural);
	}
	return @all;
}

sub identifyParagraph{
	my ($iteration, $organnames) = @_;
	my $last = $iteration - 1;
	my $select = $dbh->prepare('select paraID from '.$prefix.'_paragraphs where flag !="done" and paraID not in (select paraID from '.$prefix.'_paragraphbootstrap where ! isnull(iter'.$last.'))');
	$select->execute() or warn "$select->errstr\n";
	my @tbds = ();
	while (my ($pid) = $select->fetchrow_array()){
		push(@tbds, $pid); #to-be-determineds
	}
	
	my %newp = ();
	foreach (@tbds){
		$select = $dbh->prepare('select paragraph from '.$prefix.'_paragraphs where paraID ="'.$_.'"');
		$select->execute() or warn "$select->errstr\n";
		my ($p) = $select->fetchrow_array();
		$p =~ s#(^\s+|\s+$)##g;
		
		#if(isList($p) or isCaption($p) or isListMeasures($p) or isHeading($p) or isAcronymDef($p)) {
		if(isList($p) or isCaption($p) or isHeading($p) or isAcronymDef($p)) {
		my $update = $dbh->prepare('update '.$prefix.'_paragraphs set flag="done" where paraID="'.$_.'"');
			$update->execute() or warn "$update->errstr\n";
			next; #flag is empty, do not set to "no", no need to revisit those
		} #add isCaption and isListMeasures hong 6/25/2010
		my $olist= $organnames;
		$p = markDescription($_, $p, $iteration, $olist); #returned $p contains $rbdry and $lbdry if $p is a description
		if($p =~/$lbdry/){
			$newp{$_} = $p;	
		}
		
		
		#$p = lc($p);  #removed by Hong 6/11/10
		#$p =~ s#^(\w)#\U$1#g;
		
		#$p = markDescription($_, $p, $iteration, $organnames); #returned $p contains $rbdry and $lbdry if $p is a description
		#if($p =~/$lbdry/ and isList($p) == 0){
		#	$newp{$_} = $p;	
		#}
	}
	updateParagraphBootstrap($iteration, %newp);
}

sub isHeading{
	my $p = shift;
	return $p !~/[,;:\.-]/ or $p !~/[a-z]/;
}


#Figure 5. Heads of ...
sub isCaption{
	my $p = shift;
	$p =~ s#$lbdry##g;
	$p =~ s#$rbdry##g;
	
	return $p=~/^(Fig\.|Figs\.|Figure|Figures|Table|Plate|Plates)/i;
}

#HL: 5mm; HW: 2mm...
#also WORKER CHARCTERS.....19 and PLATE 1
sub isListMeasures{
	my $p = shift;
	$p =~ s#$lbdry##g;
	$p =~ s#$rbdry##g;
	$p =~ s#\([^()]*?[a-zA-Z][^()]*?\)# #g;  #remove (.a.)
  	$p =~ s#\[[^\]\[]*?[a-zA-Z][^\]\[]*?\]# #g;  #remove [.a.]
  	$p =~ s#{[^{}]*?[a-zA-Z][^{}]*?}# #g; #remove {.a.}
	my $non = 0;
	my $list = 0;
	
	while ($p=~/(.*?)((?:[A-Z]{2,}\W*[)(-\d\. =]+\s*[dcm]*\W*)+)(.*)/){
		my $n = $1;
		my $m = $2;
		$p = $3;
		$non += () = $n=~/\w/g;
		$list += () = $m=~/\w/g;
	}
	$non += () = $p =~/\w/g;
	return $list > $non;
}


#list of figures or abbreviations
sub isList{
	my $p = shift;
	$p =~ s#$lbdry##g;
	$p =~ s#$rbdry##g;
	#(?:, |; |\. |: |\()a([\.,:)\]]).*?\w+?.*?(?:, |; |\. |: |\()b([\.,:)\]]).*?\w+?.*?
	my $list_a = "(?:, |; |\\. |: |\\()a([\\.,:)\\]]).*?\\w+?.*?(?:, |; |\\. |: |\\()b\\1.*?\\w+?.*?";
	#(?:(?:, |; |\. |: | \d|\) |\()1([-\.,:)\]]).*?\w+?.*?(?:, |; |\. |: | \d|\) |\()2([-\.,:)\]]).*?\w+?.*?)
	my $list_1 = "(?:, |; |\\. |: |\\()1([\\.,:)\\]]).*?\\w+?.*?(?:, |; |\\. |: |\\()2\\1.*?\\w+?.*?";
	my $list_i = "(?:, |; |\\. |: |\\()i([\\.,:)\\]]).*?\\w+?.*?(?:, |; |\\. |: |\\()ii\\1.*?\\w+?.*?";
	
	my $list_1 = "(?:(?:, |; |\\. |: |\\) | \\d|\\()9([-\\.,:)\\]]).*?\\w+?.*?(?:, |; |\\. |: | \\d|\\()0([-\\.,:)\\]]).*?\\w+?.*)|(?:(?:, |; |\\. |: |\\) | \\d|\\()1([-\\.,:)\\]]).*?\\w+?.*?(?:, |; |\\. |: | \\d|\\()2([-\\.,:)\\]]).*?\\w+?.*?)|(?:(?:, |; |\\. |: |\\) | \\d|\\()2([-\\.,:)\\]]).*?\\w+?.*?(?:, |; |\\. |: | \\d|\\()3([-\\.,:)\\]]).*?\\w+?.*?)|(?:(?:, |; |\\. |: |\\) | \\d|\\()3([-\\.,:)\\]]).*?\\w+?.*?(?:, |; |\\. |: | \\d|\\()4([-\\.,:)\\]]).*?\\w+?.*?)|(?:(?:, |; |\\. |: |\\) | \\d|\\()4([-\\.,:)\\]]).*?\\w+?.*?(?:, |; |\\. |: | \\d|\\()5([-\\.,:)\\]]).*?\\w+?.*?)|(?:(?:, |; |\\. |: |\\) | \\d|\\()5([-\\.,:)\\]]).*?\\w+?.*?(?:, |; |\\. |: | \\d|\\()6([-\\.,:)\\]]).*?\\w+?.*?)|(?:(?:, |; |\\. |: |\\) | \\d|\\()6([-\\.,:)\\]]).*?\\w+?.*?(?:, |; |\\. |: | \\d|\\()7([-\\.,:)\\]]).*?\\w+?.*?)|(?:(?:, |; |\\. |: |\\) | \\d|\\()7([-\\.,:)\\]]).*?\\w+?.*?(?:, |; |\\. |: | \\d|\\()8([-\\.,:)\\]]).*?\\w+?.*?)|(?:(?:, |; |\\. |: |\\) | \\d|\\()8([-\\.,:)\\]]).*?\\w+?.*?(?:, |; |\\. |: | \\d|\\()9([-\\.,:)\\]]).*?\\w+?.*?)";
	my $list_a = "(?:(?:, |; |\\. |: |\\) | \\d|\\()a([-\\.,:)\\]]).*?\\w+?.*?(?:, |; |\\. |: | \\d|\\()b([-\\.,:)\\]]).*?\\w+?.*)|(?:(?:, |; |\\. |: |\\) | \\d|\\()b([-\\.,:)\\]]).*?\\w+?.*?(?:, |; |\\. |: | \\d|\\()c([-\\.,:)\\]]).*?\\w+?.*?)|(?:(?:, |; |\\. |: |\\) | \\d|\\()c([-\\.,:)\\]]).*?\\w+?.*?(?:, |; |\\. |: | \\d|\\()d([-\\.,:)\\]]).*?\\w+?.*?)|(?:(?:, |; |\\. |: |\\) | \\d|\\()d([-\\.,:)\\]]).*?\\w+?.*?(?:, |; |\\. |: | \\d|\\()e([-\\.,:)\\]]).*?\\w+?.*?)|(?:(?:, |; |\\. |: |\\) | \\d|\\()e([-\\.,:)\\]]).*?\\w+?.*?(?:, |; |\\. |: | \\d|\\()f([-\\.,:)\\]]).*?\\w+?.*?)|(?:(?:, |; |\\. |: |\\) | \\d|\\()f([-\\.,:)\\]]).*?\\w+?.*?(?:, |; |\\. |: | \\d|\\()g([-\\.,:)\\]]).*?\\w+?.*?)|(?:(?:, |; |\\. |: |\\) | \\d|\\()g([-\\.,:)\\]]).*?\\w+?.*?(?:, |; |\\. |: | \\d|\\()h([-\\.,:)\\]]).*?\\w+?.*?)|(?:(?:, |; |\\. |: |\\) | \\d|\\()h([-\\.,:)\\]]).*?\\w+?.*?(?:, |; |\\. |: | \\d|\\()i([-\\.,:)\\]]).*?\\w+?.*?)|(?:(?:, |; |\\. |: |\\) | \\d|\\()i([-\\.,:)\\]]).*?\\w+?.*?(?:, |; |\\. |: | \\d|\\()j([-\\.,:)\\]]).*?\\w+?.*?)";
	my $list_i = "(?:(?:, |; |\\. |: |\\) |\\()i([-\\.,:)\\]]).*?\\w+?.*?(?:, |; |\\. |: |\\()ii([-\\.,:)\\]]).*?\\w+?.*)|(?:(?:, |; |\\. |: |\\) |\\()ii([-\\.,:)\\]]).*?\\w+?.*?(?:, |; |\\. |: |\\()iii([-\\.,:)\\]]).*?\\w+?.*)|(?:(?:, |; |\\. |: |\\) |\\()iii([-\\.,:)\\]]).*?\\w+?.*?(?:, |; |\\. |: |\\()iv([-\\.,:)\\]]).*?\\w+?.*)|(?:(?:, |; |\\. |: |\\) |\\()iv([-\\.,:)\\]]).*?\\w+?.*?(?:, |; |\\. |: |\\()v([-\\.,:)\\]]).*?\\w+?.*)|(?:(?:, |; |\\. |: |\\) |\\()v([-\\.,:)\\]]).*?\\w+?.*?(?:, |; |\\. |: |\\()vi([-\\.,:)\\]]).*?\\w+?.*)|(?:(?:, |; |\\. |: |\\) |\\()vi([-\\.,:)\\]]).*?\\w+?.*?(?:, |; |\\. |: |\\()vii([-\\.,:)\\]]).*?\\w+?.*)|(?:(?:, |; |\\. |: |\\) |\\()vii([-\\.,:)\\]]).*?\\w+?.*?(?:, |; |\\. |: |\\()viii([-\\.,:)\\]]).*?\\w+?.*)|(?:(?:, |; |\\. |: |\\) |\\()viii([-\\.,:)\\]]).*?\\w+?.*?(?:, |; |\\. |: |\\()iv([-\\.,:)\\]]).*?\\w+?.*)|(?:(?:, |; |\\. |: |\\) |\\()iv([-\\.,:)\\]]).*?\\w+?.*?(?:, |; |\\. |: |\\()x([-\\.,:)\\]]).*?\\w+?.*)";
	
	my $list_1 = "(?:(?:, |- |; |\\. |: |\\) | \\d|\\()9([-\\.,:)\\]]).*?\\w+?.*?(?:, |- |; |\\. |: | \\d|\\()0([-\\.,:)\\]]).*?\\w+?.*)|(?:(?:, |- |; |\\. |: |\\) | \\d|\\()1([-\\.,:)\\]]).*?\\w+?.*?(?:, |- |; |\\. |: | \\d|\\()2([-\\.,:)\\]]).*?\\w+?.*?)|(?:(?:, |- |; |\\. |: |\\) | \\d|\\()2([-\\.,:)\\]]).*?\\w+?.*?(?:, |- |; |\\. |: | \\d|\\()3([-\\.,:)\\]]).*?\\w+?.*?)|(?:(?:, |- |; |\\. |: |\\) | \\d|\\()3([-\\.,:)\\]]).*?\\w+?.*?(?:, |- |; |\\. |: | \\d|\\()4([-\\.,:)\\]]).*?\\w+?.*?)|(?:(?:, |- |; |\\. |: |\\) | \\d|\\()4([-\\.,:)\\]]).*?\\w+?.*?(?:, |- |; |\\. |: | \\d|\\()5([-\\.,:)\\]]).*?\\w+?.*?)|(?:(?:, |- |; |\\. |: |\\) | \\d|\\()5([-\\.,:)\\]]).*?\\w+?.*?(?:, |- |; |\\. |: | \\d|\\()6([-\\.,:)\\]]).*?\\w+?.*?)|(?:(?:, |- |; |\\. |: |\\) | \\d|\\()6([-\\.,:)\\]]).*?\\w+?.*?(?:, |- |; |\\. |: | \\d|\\()7([-\\.,:)\\]]).*?\\w+?.*?)|(?:(?:, |- |; |\\. |: |\\) | \\d|\\()7([-\\.,:)\\]]).*?\\w+?.*?(?:, |- |; |\\. |: | \\d|\\()8([-\\.,:)\\]]).*?\\w+?.*?)|(?:(?:, |- |; |\\. |: |\\) | \\d|\\()8([-\\.,:)\\]]).*?\\w+?.*?(?:, |- |; |\\. |: | \\d|\\()9([-\\.,:)\\]]).*?\\w+?.*?)";
	my $list_a = "(?:(?:, |- |; |\\. |: |\\) | \\d|\\()a([-\\.,:)\\]]).*?\\w+?.*?(?:, |- |; |\\. |: | \\d|\\()b([-\\.,:)\\]]).*?\\w+?.*)|(?:(?:, |- |; |\\. |: |\\) | \\d|\\()b([-\\.,:)\\]]).*?\\w+?.*?(?:, |- |; |\\. |: | \\d|\\()c([-\\.,:)\\]]).*?\\w+?.*?)|(?:(?:, |- |; |\\. |: |\\) | \\d|\\()c([-\\.,:)\\]]).*?\\w+?.*?(?:, |- |; |\\. |: | \\d|\\()d([-\\.,:)\\]]).*?\\w+?.*?)|(?:(?:, |- |; |\\. |: |\\) | \\d|\\()d([-\\.,:)\\]]).*?\\w+?.*?(?:, |- |; |\\. |: | \\d|\\()e([-\\.,:)\\]]).*?\\w+?.*?)|(?:(?:, |- |; |\\. |: |\\) | \\d|\\()e([-\\.,:)\\]]).*?\\w+?.*?(?:, |- |; |\\. |: | \\d|\\()f([-\\.,:)\\]]).*?\\w+?.*?)|(?:(?:, |- |; |\\. |: |\\) | \\d|\\()f([-\\.,:)\\]]).*?\\w+?.*?(?:, |- |; |\\. |: | \\d|\\()g([-\\.,:)\\]]).*?\\w+?.*?)|(?:(?:, |- |; |\\. |: |\\) | \\d|\\()g([-\\.,:)\\]]).*?\\w+?.*?(?:, |- |; |\\. |: | \\d|\\()h([-\\.,:)\\]]).*?\\w+?.*?)|(?:(?:, |- |; |\\. |: |\\) | \\d|\\()h([-\\.,:)\\]]).*?\\w+?.*?(?:, |- |; |\\. |: | \\d|\\()i([-\\.,:)\\]]).*?\\w+?.*?)|(?:(?:, |- |; |\\. |: |\\) | \\d|\\()i([-\\.,:)\\]]).*?\\w+?.*?(?:, |- |; |\\. |: | \\d|\\()j([-\\.,:)\\]]).*?\\w+?.*?)";
	my $list_i = "(?:(?:, |- |; |\\. |: |\\) |\\()i([-\\.,:)\\]]).*?\\w+?.*?(?:, |- |; |\\. |: |\\()ii([-\\.,:)\\]]).*?\\w+?.*)|(?:(?:, |- |; |\\. |: |\\) |\\()ii([-\\.,:)\\]]).*?\\w+?.*?(?:, |- |; |\\. |: |\\()iii([-\\.,:)\\]]).*?\\w+?.*)|(?:(?:, |- |; |\\. |: |\\) |\\()iii([-\\.,:)\\]]).*?\\w+?.*?(?:, |- |; |\\. |: |\\()iv([-\\.,:)\\]]).*?\\w+?.*)|(?:(?:, |- |; |\\. |: |\\) |\\()iv([-\\.,:)\\]]).*?\\w+?.*?(?:, |- |; |\\. |: |\\()v([-\\.,:)\\]]).*?\\w+?.*)|(?:(?:, |- |; |\\. |: |\\) |\\()v([-\\.,:)\\]]).*?\\w+?.*?(?:, |- |; |\\. |: |\\()vi([-\\.,:)\\]]).*?\\w+?.*)|(?:(?:, |- |; |\\. |: |\\) |\\()vi([-\\.,:)\\]]).*?\\w+?.*?(?:, |- |; |\\. |: |\\()vii([-\\.,:)\\]]).*?\\w+?.*)|(?:(?:, |- |; |\\. |: |\\) |\\()vii([-\\.,:)\\]]).*?\\w+?.*?(?:, |- |; |\\. |: |\\()viii([-\\.,:)\\]]).*?\\w+?.*)|(?:(?:, |- |; |\\. |: |\\) |\\()viii([-\\.,:)\\]]).*?\\w+?.*?(?:, |- |; |\\. |: |\\()iv([-\\.,:)\\]]).*?\\w+?.*)|(?:(?:, |- |; |\\. |: |\\) |\\()iv([-\\.,:)\\]]).*?\\w+?.*?(?:, |- |; |\\. |: |\\()x([-\\.,:)\\]]).*?\\w+?.*)";
	
	
	print $p."\n" if $debug;
	if ($p =~ /$list_a/i and ($p=~/^figs?(ure|ures|[-;:,\.])/i or $p=~/^(\d+[a-z]-[a-z]|[a-z]\d+-\d+)/)){ #add [0-9][a-z] Hong 6/25/2010
		return 1;
	}elsif($p =~ /$list_1/i and ($p=~/^figs?(ure|ures|[-;:,\.])/i or $p=~/^(\d+[a-z]-[a-z]|[a-z]\d+-\d+)/)){
		return 1;
	}elsif($p =~ /$list_i/i and ($p=~/^figs?(ure|ures|[-;:,\.])/i or $p=~/^(\d+[a-z]-[a-z]|[a-z]\d+-\d+)/)){
		return 1;
	}
	return 0;
	
}

# sub isAcronymDef{
	# my $p = shift;
	# my $list_abbr = "(.*?)([A-Z]{2,})(.*)";
	# if($p =~ /$list_abbr/){	
		# my $pc = $p;
		# my $conf = 0;
		# my ($target, $wb, $wa);
		# while($pc=~/$list_abbr/){
			# $pc = $3;
			# $target = $2;
			# my $count = length($target);
			# my $words_before = takeWords($count, $1, -1); #before
			# my $words_after = takeWords($count, $3, 1); #after
			# $wb = $words_before;
			# $wa = $words_after;
			# $words_before = "@#$%^&*" if $words_before !~ /\w/;
			# $words_after = "@#$%^&*" if $words_after !~ /\w/;			
			# $words_before =~ s#[$target]##ig;
			# $words_after=~ s#[$target]##ig;
			# if(abs(length($wb) - $count) <=1 and $pc !~/\d\s*\.\s*\d/ and $words_before eq ""){
				# $conf++;
			# }elsif(abs(length($wa) - $count) <=1 and $pc !~/\d\s*\.\s*\d/ and $words_after eq ""){
				# $conf++;
			# }			
		# }
		# if ($conf >= 1){	 
			# print $p."\n";
			# return 1; 
		# }
	# }
	# return 0;
# }
# sub takeWords{
	# my ($count, $text, $dir) = @_;
	# $text =~ s#^\W+# # if $dir > 0;
	# $text =~ s#\W+$# # if $dir < 0;
	# $text =~ s#[;:,\.)(\]\[{}].*## if $dir > 0; #remove everything from the first punct on
	# $text =~ s#.*?([^;:,\.)(\]\[{}]*$)#$1# if $dir < 0;
	# $text =~ s#\s+# #g;
	# $text =~ s#^\s+##g;
	# $text =~ s#\s+$##g;
	# my $result = "";
	# my @words = split(/\s+/, $text);
	# if($dir > 0){#after
		# for(my $i = 0; $i<$count; $i++){
			# #$result.=$words[$i];
			# $result .= substr($words[$i], 0, 1);
		# }
	# }else{#before
		# my $l = @words > $count? @words-$count : 0;
		# for(my $i = @words-1; $i>=$l; $i--){
			# #$result.=$words[$i];
			# $result = substr($words[$i], 0, 1).$result;
		# }
	# }
	# #$result =~s#\W##g;
	# return $result;
	
# }

sub isAcronymDef{
	my $p = shift;
	my $list_abbr = "(.*?)([A-Z]{2,})(.*)";
	if($p =~ /$list_abbr/){	
		my $pc = $p;
		my $conf = 0;
		my ($target, $words_before, $words_after);
		while($pc=~/$list_abbr/){
			$pc = $3;
			$target = $2;
			my $count = length($target);
			$words_before = takeWords($count, $1, -1); #before
			$words_after = takeWords($count, $3, 1); #after
			if($3 !~/\d\s*\.\s*\d/ and ($words_before =~/$target/i or $words_after=~/$target/i)){ #loose up the criterion. if PTHI: Petiole Height Index; as long as all letters in PTHI are in the 4 words before or after PTHI, consider a match
				$conf++;
			}  
		}
		return 1 if $conf >= 1;	 
	}
	return 0;
}
sub takeWords{
	my ($count, $text, $dir) = @_;
	$text =~ s#^\W+# # if $dir > 0;
	$text =~ s#\W+$# # if $dir < 0;
	$text =~ s#[;:,\.)(\]\[{}].*## if $dir > 0; #remove everything from the first punct on
	$text =~ s#([^;:,\.)(\]\[{}]*)$#$1# if $dir < 0;
	$text =~ s#\s+# #g;
	$text =~ s#^\s+##g;
	$text =~ s#\s+$##g;
	my $result = "";
	my @words = split(/\s+/, $text);
	if($dir > 0){#after
		for(my $i = 0; $i<$count; $i++){
			$result .= substr($words[$i], 0, 1);
		}
	}else{#before
		my $l = @words > $count? @words-$count : 0;
		for(my $i = @words-1; $i>$l; $i--){
			$result = substr($words[$i], 0, 1).$result;
		}
	}
	$result =~s#\W##g;
	return $result;
	
}
sub updateParagraphBootstrap{
	my ($iteration, %newp) = @_;
	my @newp = keys(%newp);
	my $last = $iteration - 1;
	my %paragraphs = collectParagraphs4CurrentIteration();
	my @nowp = keys(%paragraphs);
	my %nowp = map {$_, 1} @nowp;
	my @difference = grep {!$nowp {$_}} @newp; #contains $pid
	
	my $update = $dbh->prepare('update '.$prefix.'_paragraphbootstrap set '."iter".$iteration.'='."iter".$last);
	$update->execute() or warn "$update->errstr\n";
	
	foreach (@difference){ 
		my $insert = $dbh->prepare('insert into '.$prefix.'_paragraphbootstrap (paraID, iter'.$iteration.') values("'.$_.'", '.$iteration.')');
		$insert->execute() or warn "$insert->errstr\n";
		#update paragraph text in paragraphs table
		my $updatedp = $newp{$_};
		$updatedp =~ s#(?<!\\)"#\\"#g; #escape "
		my $update = $dbh->prepare('update '.$prefix.'_paragraphs set remark = "'.$updatedp.'" where paraID = "'.$_.'"');
		$update->execute() or warn "$update->errstr\n";
	}
		
	return @difference;
}
#added by Hong, 6/11/10
sub getSentencesFrom{
	my $text = shift;
	#copied from unsupervisedOrganNameExtraction.pm
	$text =~ s/&[;#\w\d]+;/ /g; #remove HTML entities
	$text = hideBrackets($text);
	$text =~ s#:#\[COLON\]#g; #hong 6/25/2010
  	#$text =~ s#\([^()]*?[a-zA-Z][^()]*?\)# #g;  #remove (.a.)
  	#$text =~ s#\[[^\]\[]*?[a-zA-Z][^\]\[]*?\]# #g;  #remove [.a.]
  	#$text =~ s#{[^{}]*?[a-zA-Z][^{}]*?}# #g; #remove {.a.}
  	$text =~ s#_#-#g;   #_ to -
  	$text =~ s#\s+([:;\.])#\1#g;     #absent ; => absent;
  	$text =~ s#(\w)([:;\.])(\w)#\1\2 \3#g; #absent;blade => absent; blade
  	$text =~ s#(\d\s*\.)\s+(\d)#\1\2#g; #1 . 5 => 1.5
  	$text =~ s#(\sdiam)\s+(\.)#\1\2#g; #diam . =>diam.
  	$text =~ s#(\sca)\s+(\.)#\1\2#g;  # ca . =>ca.
  	$text =~ s#(\d\s+(cm|mm|dm|m)\s*)\.(\s*[^A-Z])#\1\[DOT\]\3#g;
	my @sentences = SentenceSpliter::get_sentences($text);
	
	foreach (@sentences){
		if(!/\w+/){next;}
		s#\[DOT\]#.#g;
		s#\[COLON\]#:#g;
	}
	return @sentences;
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

# xxx $lbdry ddddd $rbdry yyyy => only dddd are description sentences
# if it is a description, then it will come out with $ldbry inserted
sub markDescription{
	my ($pid, $p, $iteration, $organnames) = @_;
	my $newp = "";
	my $start = 0;
	my $end = 0;
	
	my $pns = countProperNouns($p);
	
	my @sentences = getSentencesFrom($p); 
	@sentences = grep (/\w+/, @sentences);
	if(@sentences ==0){
		my $update = $dbh->prepare('update '.$prefix.'_paragraphs set flag="not" where paraID="'.$pid.'"');
		$update->execute() or warn "$update->errstr\n";
		return "";
	}
	my $meanlength = length($p)/@sentences; #count by characters
	my $totalwords = () = $p =~ /\b[a-zA-Z-]+\b/g;
	if($totalwords < 2){
		return "";
	}
	my $total = @sentences;
	
	#Hong added 6/11/10
	if($total <= 3 and $pns>0){
		my $update = $dbh->prepare('update '.$prefix.'_paragraphs set flag="not" where paraID="'.$pid.'"');
		$update->execute() or warn "$update->errstr\n";
		return "";
	
	}
		
	#first set of results: 1/3; 10; 1/3; 30, 0.5
	#second set of results:1/3; 5; 1/3; 30, 0.5
	#third set of results:1/3; 5; 1/3; 15, 0.5
			#fourth: 1/3, 10,1/3,30, 0.3,
	if($iteration**($itfactor1)*$total >= $sentcountthresh && $iteration**($itfactor2)*$meanlength >= $charcountthresh){
	#if($iteration**(1/3)*$total >= 10){
		my $update = $dbh->prepare('update '.$prefix.'_paragraphs set flag="checked" where paraID="'.$pid.'"');
		$update->execute() or warn "$update->errstr\n";
		my $fits = 0;
		foreach (@sentences){

			my $count = 0;
			($count, $organnames) = startsWithOrgan($_, $organnames); #remove matched organ names
			if($count > 0){
				#$fits++;
				$fits +=$count;
				$newp =~ s#$rbdry##;
				$end = 0;
				if($start == 0){
					$newp .= $lbdry; 
					$start = 1;
				}
			}else{
				if($start == 1 && $end==0){
					$newp .= $rbdry;
					$end = 1;
				} 
			}
			$newp .= $_." ";
		}
		#1-3: 0.5
		
		#if($fits >= $total * $organdensitythresh or $fits >=3){
		if($fits >= $totalwords * 0.05 and $fits >= $pns){ #hong 6/24/2010: change base from # of sent to # of words, add fits >= pns
			return $newp; #descriptions
		}
		#not descriptions

	}else{ #paragraphs not even meeting the threshold criteria
		my $update = $dbh->prepare('update '.$prefix.'_paragraphs set flag="not" where paraID="'.$pid.'"');
		$update->execute() or warn "$update->errstr\n";
	}
	return "";
}

#Hong added 6/11/10
# sub containsProperNouns{
	# my $text = shift;
	# $text =~ s#\([^()]*?[a-zA-Z][^()]*?\)# #g;  #remove (.a.)
  	# $text =~ s#\[[^\]\[]*?[a-zA-Z][^\]\[]*?\]# #g;  #remove [.a.]
  	# $text =~ s#{[^{}]*?[a-zA-Z][^{}]*?}# #g; #remove {.a.}
	# if($text =~/\w\s+[A-Z][a-z]/){
		# return 1;
	# }
	# return 0;
# }

#Hong added 6/25/2010
sub countProperNouns{
	my $text = shift;
	$text =~ s#\([^()]*?[a-zA-Z][^()]*?\)# #g;  #remove (.a.)
  	$text =~ s#\[[^\]\[]*?[a-zA-Z][^\]\[]*?\]# #g;  #remove [.a.]
  	$text =~ s#{[^{}]*?[a-zA-Z][^{}]*?}# #g; #remove {.a.}
	
	my $count = () = $text =~/[\w;,]\s+[A-Z](\.|[a-zA-Z])/g;
	return $count;
}


sub countUniqueEntities{
	my($sent, $l, $r, $entities) = @_;
	$sent =~ s#^\d+[a-z]?\W+([A-Z])#$1#; #remove bullets hong 6/25/2010
	$sent =~ s#\b(a|the)\b##gi; #hong 6/25/2010
	$sent =~ s#^\W+##g; #-   Hong 6/24/2010
	$sent =~ s#(^\s+|\s+$)##g;
	my $msent = "";
	my $count = 0;
	while($sent=~/(.*?)\b($entities)\b(.*)/i){
		$msent .=$1.$l.$2.$r;
		$sent = $3;
		my $o = $2;
		$entities =~ s#\b$o\b##ig;	
		$entities =~ s#\|+#|#g;
		$entities =~ s#^\|##g;
		$entities =~ s#\|$##g;
		$count++;
	}
	$msent .=$sent;
	$sent = $msent;	
	return ($count, $sent, $entities);	
}

#return the number of matches and the new list of $organnames (after the removal of matched organ names)
sub startsWithOrgan{
	my ($sent, $organnames) = @_;
	my $count = 0;
	
	($count, $sent, $organnames) = countUniqueEntities($sent, "<", ">", $organnames);
	
	#count leading organs
	my $double = 1;
	#if($sent=~/^<[A-Z]/){ 
	if($sent=~/^</ or $sent=~/:\s*</){ #hong 6/24/2010 "Minor: <hairs", or "<hairs
		$double = 2;
	}
	
	
	#hong 6/25/2010
	# while($sent=~/(.*?(:|,|^)\s*)<[^>]+>.*?(,|\.|;|$)(.*)/){
		# $count++;
		# $sent = $1.$4;
	# }
	# #not include "of"
	# my $prep ="above|across|after|along|around|as|at|before|beneath|between|beyond|by|for|from|in|into|near|off|on|onto|out|outside|over|than|throughout|toward|towards|up|upward|with|without";

	# while($sent=~/(.*?(?::|,|^))\s*(\w+)\s+<[^>]+>.*?(,|\.|;|$)(.*)/){ #there is 1 word before <>
		# if($2 !~/\b($prep)\b/i){
			# $count++;
		# }
		# $sent = $1.$4;
	# }
	
	# while($sent=~/(.*?(?::|,|^))\s*(\w+)\s+\w+\s+<[^>]+>.*?(,|\.|;|$)(.*)/){#two words before <>
		# if($2 !~/\b($prep)\b/i){
			# $count++;
		# }
		# $sent = $1.$4;
	# }

	
	return ($count*$double, $organnames);
}

# sub startsWithOrgan{
	# my ($sent, $organnames) = @_;
	# lc($sent);
	# my $leadwords = join(" ", unsupervisedOrganNameExtraction::getfirstnwords($sent, 3));
	# $leadwords =~ s#-#_#g;
	# if($leadwords =~ /\b($organnames)(?!(\s*\]|\s*\)|\s*}|\s*>|\w))/i){ #do not match [[ worker ]]
		# $organnames =~ s#\b$1\b##g;
		# $organnames =~ s#\|+#|#g;
		# $organnames =~ s#^\|##g;
		# $organnames =~ s#\|$##g;
		# return (1, $organnames);
	# }
	# return (0, $organnames);
# }

#################################################################################################################
###########                                update organ bootstrap table for an interation                                    ###########
#################################################################################################################

sub updateOrganNameBootstrapTable{
	my ($iteration, @neworgan_names) = @_;
	my $last = $iteration - 1;
	my @noworgan_names = selectOrganNames("iter".$last);
	my %noworgan_names = map {$_, 1} @noworgan_names;
	my @difference = grep {!$noworgan_names {$_}} @neworgan_names;
	
	my $update = $dbh->prepare('update '.$prefix.'_organnamebootstrap set '."iter".$iteration.'='."iter".$last);
	$update->execute() or warn "$update->errstr\n";
	
	foreach (@difference){ 
		my $insert = $dbh->prepare('insert into '.$prefix.'_organnamebootstrap (organname, iter'.$iteration.') values("'.$_.'", '.$iteration.')');
		$insert->execute() or warn "$insert->errstr\n";
	}
		
	return @difference;
}


#################################################################################################################
###########                                collect paragraphs for organ name extraction in the current iteration                                    ###########
#################################################################################################################

sub collectParagraphs4CurrentIteration{
	#my $column = shift;
	my %paragraphs = ();
	my $select = $dbh->prepare('select paraID,remark from '.$prefix.'_paragraphs where paraID in (select paraID from '.$prefix.'_paragraphbootstrap)');
	$select->execute() or warn "$select->errstr\n";
	while( my ($pid, $p) = $select -> fetchrow_array()) { #print values
		#$p may has $rbdry and $lbdry, take only text in between $l and $r
		$p =~ s#.*?$lbdry##;
		$p =~ s#$rbdry.*##;
		$p =~ s#"##g; # to avoid syntax errors in organnameextraction
		$paragraphs{$pid} = $p
	}
	return %paragraphs;
}



#################################################################################################################
###########                                collecting organ names                                     ###########
#################################################################################################################

sub collectOrganNames{
	my @organ_names = ();
	my $sth = $dbh -> prepare("SELECT distinct tag FROM ".$prefix."_sentence where not isnull(tag)");
	$sth -> execute() or print "$sth->errstr\n";

	while( my ($o) = $sth -> fetchrow_array()) { #print values
		  push(@organ_names, $o) if $o !~ /\b($false_organs)\b/ and $o !~ /^[a-z]$/i;
	}

	#clean up
	#$dbh -> disconnect();
	return @organ_names;
}

sub selectOrganNames{
	#my $column = shift;
	my @organ_names = ();
	my $select = $dbh->prepare('select distinct organname from '.$prefix.'_organnamebootstrap');
	$select->execute() or warn "$select->errstr\n";
	while( my $o = $select -> fetchrow_array()) { #print values
		  push(@organ_names, $o) if $o !~ /\b($false_organs)\b/ and $o !~ /^[a-z]$/i;
	}
	return @organ_names;
}
#hong added 6/14/10
sub selectDomainTerms{
	my $n = $unsupervisedOrganNameExtraction::NUMBERS;
	my $pn =$unsupervisedOrganNameExtraction::PRONOUN;
	my $c = $unsupervisedOrganNameExtraction::CHARACTER;
	my $p = $unsupervisedOrganNameExtraction::PROPOSITION;
	my $cl = $unsupervisedOrganNameExtraction::CLUSTERSTRINGS;
	my $s= $unsupervisedOrganNameExtraction::SUBSTRUCTURESTRINGS;
	my $sp = $NounHeuristics::STOP;
	
	my $ignores = $n."|".$pn."|".$c."|".$p."|".$cl."|".$s."|".$sp;
	$ignores =~ s#\|+#|#g;
	$ignores =~ s#^\|##g;
	$ignores =~ s#\|$##g;
	
	my @domain_terms = ();
	my $select = $dbh->prepare('select distinct word from '.$prefix.'_wordpos');
	$select->execute() or warn "$select->errstr\n";
	while( my ($o) = $select -> fetchrow_array()) { #print values
		if($o =~ /^_/ or $o =~ /_$/){
			$o =~ s#_##g;
		}
		if($o !~/\b($ignores)\b/i and $o !~ /^[a-z]$/i and $o =~/\w/){
			push(@domain_terms, $o)  ;
		}		
	}
	return @domain_terms;
}

##################################################################################################################
# 	populateParagrahTable
##############################################################################
sub populateParagraphTable{

my @dir_contents; 
my $file; #file name
opendir(DIR,$source) || die("Cannot open directory !\n"); 
@dir_contents = readdir(DIR);
closedir(DIR);

foreach $file (@dir_contents){ #parse all the files in Plain_text
    if(!(($file eq ".") || ($file eq ".."))){
		print $file."\n" if $debug;
		
		open (MYFILE, "$source\\$file") || die ("Could not open the file.");
		my @p = <MYFILE>; #paragraph -> read as lines
		@p = grep {!($_ =~ /^(\s+)?$/)} @p;
		my $count = 0;
		foreach my $p (@p){
			#chomp($p);
			my $pid = $file.'p'.$count;
			$p =~ s#-#\\-#g;
			$p =~ s#"#\\"#g;
			$p =~ s#&\w+;# #g;
			$p =~ s#[^a-zA-Z0-9! "\#$%&'()*+,-\./:;<=>?@\[\]^_`\\{|}~]# #g; #non-printables => 1 space
			$p =~ s#\s+# #g;
			$p =~ s#(^\s+|\s+$)##g;
			
			my $insert = $dbh->prepare('insert into '.$prefix.'_paragraphs (paraID, paragraph, remark) values("'.$pid.'", "'.$p.'", "")');
			$insert->execute() or warn "$insert->errstr: $pid => $p\n";
			
			$count++;
		}
		close(MYFILE);
	}
}
#my $update = $dbh->prepare('update '.$prefix.'_paragraphs set remark=paragraph');
#$update->execute();
}
    
