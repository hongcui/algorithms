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
use lib 'C:\\Docume~1\\hongcui\\Desktop\\WordNov2009\\Description_Extraction\\paragraphExtraction\\UnsupervisedClauseMarkup\\';
#use lib 'C:\\Docume~1\\hongcui\\Desktop\\WordNov2009\\Description_Extraction\\paragraphExtraction\\';
use SentenceSpliter;
use SeedDescriptionExtraction;
use unsupervisedOrganNameExtraction;


if(@ARGV != 2) {
print "\nbootstrapDescriptionExtraction.pl identifies morphological descriptions from heterogenous source documents (Plain_text) \n";
print "\nUsage:\n";
print "\tperl bootstrapDescriptionExtraction.pl absolute-path-of-source-dir databasename\n";
print "\tResults will be saved to the database specified \n";
exit(1);
}

print stdout "Initialized:\n";
###########################################################################################
#########################                                     #############################
#########################set up global variables              #############################
###########################################################################################

my $dir_to_open = $ARGV[0]."\\";#textfile
my $db = $ARGV[1];

my $lbdry = "<@<";
my $rbdry = ">@>";
#my $dir_to_open = "C:\\Documents and Settings\\hongcui\\Desktop\\WordNov2009\\Description_Extraction\\extractionSource\\Plain_text";
my $dir_to_save = "C:\\Documents and Settings\\hongcui\\Desktop\\WordNov2009\\Description_Extraction\\extractionData\\Description_paragraphs";
my $false_organs="ignore|\[parenttag\]|general|ditto";

my $host = "localhost";
my $user = "termsuser";
my $password = "termspassword";
my $dbh = DBI->connect("DBI:mysql:host=$host", $user, $password)
or die DBI->errstr."\n";

my $test = $dbh->prepare('create database if not exists '.$db.' CHARACTER SET utf8') or die $dbh->errstr."\n";
$test->execute() or die $test->errstr."\n";

$test = $dbh->prepare('use '.$db) or die $dbh->errstr."\n";
$test->execute() or die $test->errstr."\n";

#create paragraph database (add a column for each iteration)
my $create = $dbh->prepare('create table if not exists paragraphs (paragraphID varchar(100) not null unique, paragraph text(20000), remark text(2000), primary key (paragraphID))');
$create->execute() or warn "$create->errstr\n";
my $update = $dbh->prepare('update paragraphs set remark=paragraph');
$update->execute();
#my $del = $dbh->prepare('delete from paragraphs');
#$del->execute();

#populate paragraph database
my $select = $dbh->prepare('select count(paragraphID) from paragraphs');
$select->execute() or warn "$select->errstr\n";
my ($count) = $select->fetchrow_array();
if($count==0){
	populateParagraphTable();
}

#create paragraph bootstrap tabe
my $del = $dbh->prepare('drop table if exists paragraphbootstrap');
$del->execute();
$create = $dbh->prepare('create table if not exists paragraphbootstrap (paragraphID varchar(100) not null unique,iter0 char(1), primary key (paragraphID))');
$create->execute() or warn "$create->errstr\n";



#create organ name database
$del = $dbh->prepare('drop table if exists organnamebootstrap');
$del->execute();
$create = $dbh->prepare('create table if not exists organnamebootstrap (organname varchar(100) not null unique,iter0 char(1), primary key (organname))');
$create->execute() or warn "$create->errstr\n";


#identifies seed paragraphs (set in iternation0 )
my @seeds = SeedDescriptionExtraction::collectSeedParagraphs($db, "paragraphs");
foreach (@seeds){
	my $insert = $dbh->prepare('insert into paragraphbootstrap values("'.$_.'", 0)');
	$insert->execute() or warn "$insert->errstr\n";
}

#identifies seed organs
print "run unsupervisedOrganExtraction.pl for iteration 0\n";
my %paragraphs = collectParagraphs4CurrentIteration();
unsupervisedOrganNameExtraction::extractOrganNames($db, %paragraphs);

my @organ_names = collectOrganNames();
foreach (@organ_names){
	my $insert = $dbh->prepare('insert into organnamebootstrap values("'.$_.'", 0)');
	$insert->execute() or warn "$insert->errstr\n";
}

my $new = 0;
my $iteration = 0;
do{
	$iteration++;
	#add a column in organName and paragraph table
	my $alter = $dbh->prepare('alter table paragraphbootstrap add '."iter".$iteration.' int');
	$alter->execute() or warn "$alter->errstr\n";
	my $alter = $dbh->prepare('alter table organnamebootstrap add '."iter".$iteration.' int');
	$alter->execute() or warn "$alter->errstr\n";
	$new = bootstrap($iteration);
	
}while $new > 0;


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
	my $update = $dbh->prepare('update paragraphbootstrap set iter'.$iteration.'=iter'.$round);
	$update->execute() or warn "$update->errstr\n";
	identifyParagraph($iteration, $organnames);
	###TODO
	
	#run unsupervise.pl to learn more organ names
	print "run unsupervisedOrganExtraction.pl for iteration ".$iteration;
	my %paragraphs = collectParagraphs4CurrentIteration();
	unsupervisedOrganNameExtraction::extractOrganNames($db, %paragraphs);
	@organ_names = collectOrganNames();
	#update organ name bootstrap table
	$new = updateOrganNameBootstrapTable($iteration, @organ_names);
}

sub getAlsoPlurals{
	my @organnames = @_;
	my @all  = @organnames;
	foreach (@organnames){
		my $select = $dbh->prepare('select plural from singularplural where singular ="'.$_.'"');
		$select->execute() or warn "$select->errstr\n";
		my ($plural) = $select->fetchrow_array();
		push(@all, $plural);
	}
	return @all;
}
sub identifyParagraph{
	my ($iteration, $organnames) = @_;
	my $last = $iteration - 1;
	my $select = $dbh->prepare('select paragraphID from paragraphs where paragraphID not in (select paragraphID from paragraphbootstrap where iter'.$last.'="y")');
	$select->execute() or warn "$select->errstr\n";
	my @tbds = ();
	while (my ($pid) = $select->fetchrow_array()){
		push(@tbds, $pid); #to-be-determineds
	}
	
	my %newp = ();
	foreach (@tbds){
		$select = $dbh->prepare('select paragraph from paragraphs where paragraphID ="'.$_.'"');
		$select->execute() or warn "$select->errstr\n";
		my ($p) = $select->fetchrow_array();
		
		$p = markDescription($p, $iteration, $organnames); #returned $p contains $rbdry and $lbdry if $p is a description
		if($p =~/$lbdry/ and isList($p) == 0){
			$newp{$_} = $p;	
		}
	}
	updateParagraphBootstrap($iteration, %newp);
}

#list of figures or abbreviations
sub isList{
	my $p = shift;
	my $list_a = "[,;\\.:] a([\\.,:)\\]]).*?\\w+?.*?[.;\\.] b\\1.*?\\w+?.*?";
	my $list_1 = "[,;\\.:] 1([\\.,:\\]]).*?\\w+?.*?[.;\\.] 2\\1.*?\\w+?.*?";
	my $list_i = "[,;\\.:] i([\\.,:\\]]).*?\\w+?.*?[.;\\.] ii\\1.*?\\w+?.*?";
	my $list_abbr = "[,;\\.:] [A-Z]{2,}([:,])([^A-Z]{4,})[,;\\.] [A-Z]{2,}\\1([^A-Z]{4,})";
	if ($p =~ /$list_a/i and $p=~/^fig(ure|ures|\.)/i){
		return 1;
	}elsif($p =~ /$list_1/i and $p=~/^fig(ure|ures|\.)/i){
		return 1;
	}elsif($p =~ /$list_i/i and $p=~/^fig(ure|ures|\.)/i){
		return 1;
	}elsif($p =~ /$list_abbr/ and $2!~/\d/ and $3!~/\d/){
		return 1;	 
	}else{
		return 0;
	}
}
sub updateParagraphBootstrap{
	my ($iteration, %newp) = @_;
	my @newp = keys(%newp);
	my $last = $iteration - 1;
	my %paragraphs = collectParagraphs4CurrentIteration();
	my @nowp = keys(%paragraphs);
	my %nowp = map {$_, 1} @nowp;
	my @difference = grep {!$nowp {$_}} @newp; #contains $pid
	
	my $update = $dbh->prepare('update paragraphbootstrap set '."iter".$iteration.'='."iter".$last);
	$update->execute() or warn "$update->errstr\n";
	
	foreach (@difference){ 
		my $insert = $dbh->prepare('insert into paragraphbootstrap (paragraphID, iter'.$iteration.') values("'.$_.'", '.$iteration.')');
		$insert->execute() or warn "$insert->errstr\n";
		#update paragraph text in paragraphs table
		my $updatedp = $newp{$_};
		$updatedp =~ s#(?<!\\)"#\\"#g;
		my $update = $dbh->prepare('update paragraphs set remark = "'.$updatedp.'" where paragraphID = "'.$_.'"');
		$update->execute() or warn "$update->errstr\n";
	}
		
	return @difference;
}


# xxx $lbdry ddddd $rbdry yyyy => only dddd are description sentences
# if it is a description, then it will come out with $ldbry inserted
sub markDescription{
	my ($p, $iteration, $organnames) = @_;
	my $newp = "";
	my $start = 0;
	my $end = 0;
	my @sentences = SentenceSpliter::get_sentences($p);
	@sentences = grep (/\w+/, @sentences);
	my $meanlength = length($p)/@sentences; #count by characters
	
	my $total = @sentences;
	if($iteration**(1/3)*$total >= 10 && $iteration**(1/3)*$meanlength >= 30){
	#if($iteration**(1/3)*$total >= 10){
		my $fits = 0;
		foreach (@sentences){
			s#\s*$##;
			my $count = 0;
			($count, $organnames) = startsWithOrgan($_, $organnames); #remove matched organ names
			if($count > 0){
				$fits++;
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
		
		if($fits >= $total * 0.5){
			return $newp;
		}
	}
	return "";
}

#return the number of matches and the new list of $organnames (after the removal of matched organ names)
sub startsWithOrgan{
	my ($sent, $organnames) = @_;
	lc($sent);
	#TODO: $organnames should contain singular AND pl forms of an organ name
	#TODO: $organnames should be a '|||'
	my $leadwords = join(" ", unsupervisedOrganNameExtraction::getfirstnwords($sent, 3));
	$leadwords =~ s#-#_#g;
	if($leadwords =~ /\b($organnames)(?!(\s*\]|\s*\)|\s*}|\s*>|\w))/i){ #do not match [[ worker ]]
		$organnames =~ s#\b$1\b##g;
		$organnames =~ s#\|+#|#g;
		$organnames =~ s#^\|##g;
		$organnames =~ s#\|$##g;
		return (1, $organnames);
	}
	return (0, $organnames);
}

#################################################################################################################
###########                                update organ bootstrap table for an interation                                    ###########
#################################################################################################################

sub updateOrganNameBootstrapTable{
	my ($iteration, @neworgan_names) = @_;
	my $last = $iteration - 1;
	my @noworgan_names = selectOrganNames("iter".$last);
	my %noworgan_names = map {$_, 1} @noworgan_names;
	my @difference = grep {!$noworgan_names {$_}} @neworgan_names;
	
	my $update = $dbh->prepare('update organnamebootstrap set '."iter".$iteration.'='."iter".$last);
	$update->execute() or warn "$update->errstr\n";
	
	foreach (@difference){ 
		my $insert = $dbh->prepare('insert into organnamebootstrap (organname, iter'.$iteration.') values("'.$_.'", '.$iteration.')');
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
	my $select = $dbh->prepare('select paragraphID,remark from paragraphs where paragraphID in (select paragraphID from paragraphbootstrap)');
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
	my $sth = $dbh -> prepare("SELECT distinct tag FROM sentence where not isnull(tag)");
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
	my $select = $dbh->prepare('select distinct organname from organnamebootstrap');
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
		print $file."\n";
		
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
			my $insert = $dbh->prepare('insert into paragraphs values("'.$pid.'", "'.$p.'", "")');
			$insert->execute() or warn "$insert->errstr: $pid => $p\n";
			
			$count++;
		}
		close(MYFILE);
	}
}
my $update = $dbh->prepare('update paragraphs set remark=paragraph');
$update->execute();
}
    
