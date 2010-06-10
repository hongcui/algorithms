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


if(@ARGV != 5) {
print "\nbootstrapDescriptionExtraction.pl identifies morphological descriptions from heterogenous source documents (Plain_text) \n";
print "\nUsage:\n";
print "\tperl bootstrapDescriptionExtraction.pl absolute-path-of-source-dir databasename mode seedfilie prefix\n";
print "\tResults will be saved to the database specified \n";
exit(1);
}

# print stdout "Initialized:\n";
# ##################################################################
# #########################                                                           #####################
# #########################        set up global variables              ##################### 
####################################################################

my $dir_to_open = $ARGV[0]."\\";#textfile
my $db = $ARGV[1];
my $mode = $ARGV[2];
my $seedfile =$ARGV[3];
my $prefix = $ARGV[4];

my $debug = 0;

	#########################################
	# configurable parameters:
	#########################################

my $itfactor1 = 1/3;
my $sentcountthresh = 3;
my $itfactor2 = 1/3;
my $charcountthresh = 20;
my $organdensitythresh = 0.5;

my $lbdry = "<@<";
my $rbdry = ">@>";
#my $dir_to_open = "C:\\Documents and Settings\\hongcui\\Desktop\\WordNov2009\\Description_Extraction\\extractionSource\\Plain_text";
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
#create paragraph database (add a column for each iteration)
my $del = $dbh->prepare('drop table if exists '.$prefix.'_paragraphs');
$del->execute();
my $create = $dbh->prepare('create table if not exists '.$prefix.'_paragraphs (paraID varchar(100) not null unique, paragraph text(20000), remark text(20000), flag varchar(10), primary key (paraID))');
$create->execute() or warn "$create->errstr\n";
my $update = $dbh->prepare('update '.$prefix.'_paragraphs set remark=paragraph');
$update->execute();


#populate paragraph database
my $select = $dbh->prepare('select count(paraID) from '.$prefix.'_paragraphs');
$select->execute() or warn "$select->errstr\n";
my ($count) = $select->fetchrow_array();
if($count==0){
	populateParagraphTable();
}

#create paragraph bootstrap tabe
my $del = $dbh->prepare('drop table if exists '.$prefix.'_paragraphbootstrap');
$del->execute();
$create = $dbh->prepare('create table if not exists '.$prefix.'_paragraphbootstrap (paraID varchar(100) not null unique,iter0 char(1), primary key (paraID))');
$create->execute() or warn "$create->errstr\n";



#create organ name table
$del = $dbh->prepare('drop table if exists '.$prefix.'_organnamebootstrap');
$del->execute();
$create = $dbh->prepare('create table if not exists '.$prefix.'_organnamebootstrap (organname varchar(100) not null unique,iter0 char(1), primary key (organname))');
$create->execute() or warn "$create->errstr\n";

#create performance table
my $create = $dbh->prepare('create table if not exists paragraph_extraction_evaluation (timestmp timestamp DEFAULT CURRENT_TIMESTAMP, dataset varchar(500), setting varchar(100), precison float(5,3), recall float(5,3), primary key (timestmp))');
$create->execute() or warn "$create->errstr\n";

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
@seeds = SeedDescriptionExtraction::collectSeedParagraphs($db, "paragraphs", $prefix);
}
foreach (@seeds){
	my $insert = $dbh->prepare('insert into '.$prefix.'_paragraphbootstrap values("'.$_.'", 0)');
	$insert->execute() or warn "$insert->errstr\n";
}

#identifies seed organs
print "run unsupervisedOrganExtraction.pl for iteration 0\n";
my %paragraphs = collectParagraphs4CurrentIteration();
unsupervisedOrganNameExtraction::extractOrganNames($db, $mode, $prefix, %paragraphs);

 @organ_names = collectOrganNames();
foreach (@organ_names){
	my $insert = $dbh->prepare('insert into '.$prefix.'_organnamebootstrap values("'.$_.'", 0)');
	$insert->execute() or warn "$insert->errstr\n";
}


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

processunchecked($iteration+1);

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
my $all = 0;
my $got = 0;
my $got_good = 0;
my $select = $dbh->prepare('select count(paraID) from '.$prefix.'_paragraphs_benchmark where isDescription="y"');
$select->execute() or warn '$select->errstr\n';
($all) = $select->fetchrow_array();
$select = $dbh->prepare('select count(paraID) from '.$prefix.'_paragraphbootstrap');
$select->execute() or warn '$select->errstr\n';
($got) = $select->fetchrow_array();
$select = $dbh->prepare('select count(paraID) from '.$prefix.'_paragraphbootstrap where paraID in (select paraID from '.$prefix.'_paragraphs_benchmark where isDescription="y")');
$select->execute() or warn '$select->errstr\n';
($got_good) = $select->fetchrow_array();
#timestamp timestampe not null unique, dataset varchar(500), setting varchar(100), precision float, recall float, primary key (timestamp))
my $precision = $got_good/$got;
my $recall = $got_good/$all;
my $q = 'insert into paragraph_extraction_evaluation (dataset, setting, precison, recall) values("'.$prefix.'","'.$itfactor1.'/'.$sentcountthresh.'/'.$itfactor2.'/'.$charcountthresh.'/'.$organdensitythresh.'", '.$precision.', '.$recall.')';
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


#####################################################
#                   sub-routines
#####################################################

sub processunchecked{
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

	my %newp = ();
	foreach (@tbds){
		$select = $dbh->prepare('select paragraph from '.$prefix.'_paragraphs where paraID ="'.$_.'"');
		$select->execute() or warn "$select->errstr\n";
		my ($p) = $select->fetchrow_array();
		$p =~ s#(^\s+|\s+$)##g;
		$p =~ s#\b($organnames)\b#<$1>#gi;
		if($p=~/(^|\d[a-z]?[,\.]?\s*)<[A-Z]/){
			$newp{$_} = $p;	
		}
	}
	updateParagraphBootstrap($iteration, %newp);
}

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
	my $select = $dbh->prepare('select paraID from '.$prefix.'_paragraphs where paraID not in (select paraID from '.$prefix.'_paragraphbootstrap where ! isnull(iter'.$last.'))');
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
		$p = lc($p);
		$p =~ s#^(\w)#\U$1#g;
		$p = markDescription($_, $p, $iteration, $organnames); #returned $p contains $rbdry and $lbdry if $p is a description
		if($p =~/$lbdry/ and isList($p) == 0){
			$newp{$_} = $p;	
		}
	}
	updateParagraphBootstrap($iteration, %newp);
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
	
	my $list_abbr = "(.*?)([A-Z]{2,})(.*)";
	print $p."\n" if $debug;
	if ($p =~ /$list_a/i and $p=~/^figs?(ure|ures|[-;:,\.])/i){
		return 1;
	}elsif($p =~ /$list_1/i and $p=~/^figs?(ure|ures|[-;:,\.])/i){
		return 1;
	}elsif($p =~ /$list_i/i and $p=~/^figs?(ure|ures|[-;:,\.])/i){
		return 1;
	}elsif($p =~ /$list_abbr/){	
		my $pc = $p;
		my $conf = 0;
		while($pc=~/$list_abbr/){
			$pc = $3;
			my $target = $2;
			my $count = length($target);
			my $words_before = takeWords($count, $1, -1);
			my $words_after = takeWords($count, $3, 1);
			if($3 !~/\d\s*\.\s*\d/ and ($words_before =~/$target/i or $words_after=~/$target/i)){
				$conf++;
			}  
			
		}
		return 1 if $conf > 1;	 
	}
	return 0;
	
}

sub takeWords{
	my ($count, $text, $dir) = @_;
	$text =~ s#\W# #g;
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
		for(my $i = @words-1; $i>@words-1-$count; $i--){
			$result = substr($words[$i], 0, 1).$result;
		}
	}
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
		$updatedp =~ s#(?<!\\)"#\\"#g;
		my $update = $dbh->prepare('update '.$prefix.'_paragraphs set remark = "'.$updatedp.'" where paraID = "'.$_.'"');
		$update->execute() or warn "$update->errstr\n";
	}
		
	return @difference;
}



# xxx $lbdry ddddd $rbdry yyyy => only dddd are description sentences
# if it is a description, then it will come out with $ldbry inserted
sub markDescription{
	my ($pid, $p, $iteration, $organnames) = @_;
	my $newp = "";
	my $start = 0;
	my $end = 0;
	my @sentences = SentenceSpliter::get_sentences($p);
	@sentences = grep (/\w+/, @sentences);
	if(@sentences ==0){
		return "";
	}
	my $meanlength = length($p)/@sentences; #count by characters
	
	my $total = @sentences;
	#first set of results: 1/3; 10; 1/3; 30, 0.5
	#second set of results:1/3; 5; 1/3; 30, 0.5
	#third set of results:1/3; 5; 1/3; 15, 0.5
			#fourth: 1/3, 10,1/3,30, 0.3,
	if($iteration**($itfactor1)*$total >= $sentcountthresh && $iteration**($itfactor2)*$meanlength >= $charcountthresh){
	#if($iteration**(1/3)*$total >= 10){
		my $fits = 0;
		foreach (@sentences){
			s#\s*$##;
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

		if($fits >= $total * $organdensitythresh or $fits >=3){
			return $newp;
		}
		
		my $update = $dbh->prepare('update '.$prefix.'_paragraphs set flag="checked" where paraID="'.$pid.'"');
		$update->execute() or warn "$update->errstr\n";
	}else{
		my $update = $dbh->prepare('update '.$prefix.'_paragraphs set flag="not" where paraID="'.$pid.'"');
		$update->execute() or warn "$update->errstr\n";
		}
	return "";
}

#return the number of matches and the new list of $organnames (after the removal of matched organ names)
sub startsWithOrgan{
	my ($sent, $organnames) = @_;
	$sent =~ s#(^\s+|\s+$)##g;
	$sent =~ s#\b($organnames)\b#<\1>#ig;
	#count leading organs
	my $count = 0;
	my $double = 1;
	if($sent=~/^<[A-Z]/){
		$double = 2;
	}
	while($sent=~/(.*?(,|^)\s*)<[^>]+>.*?(,|\.|;|$)(.*)/){
		$count++;
		$sent = $1.$4;
	}
	my $prep ="above|across|after|along|around|as|at|before|beneath|between|beyond|by|for|from|in|into|near|of|off|on|onto|out|outside|over|than|throughout|toward|towards|up|upward|with|without";

	while($sent=~/(.*?,)\s*(\w+)\s+<[^>]+>.*?(,|\.|;|$)(.*)/){
		if($2 !~/\b($prep)\b/i){
			$count++;
		}
		$sent = $1.$4;
	}
	
	while($sent=~/(.*?,)\s*(\w+)\s+\w+\s+<[^>]+>.*?(,|\.|;|$)(.*)/){
		if($2 !~/\b($prep)\b/i){
			$count++;
		}
		$sent = $1.$4;
	}

	
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

##################################################################################################################
# 	populateParagrahTable
##############################################################################
sub populateParagraphTable{

my @dir_contents; 
my $file; #file name
opendir(DIR,$dir_to_open) || die("Cannot open directory !\n"); 
@dir_contents = readdir(DIR);
closedir(DIR);

foreach $file (@dir_contents){ #parse all the files in Plain_text
    if(!(($file eq ".") || ($file eq ".."))){
		print $file."\n" if $debug;
		
		open (MYFILE, "$dir_to_open\\$file") || die ("Could not open the file.");
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
			my $insert = $dbh->prepare('insert into '.$prefix.'_paragraphs (paraID, paragraph, remark) values("'.$pid.'", "'.$p.'", "")');
			$insert->execute() or warn "$insert->errstr: $pid => $p\n";
			
			$count++;
		}
		close(MYFILE);
	}
}
my $update = $dbh->prepare('update '.$prefix.'_paragraphs set remark=paragraph');
$update->execute();
}
    
