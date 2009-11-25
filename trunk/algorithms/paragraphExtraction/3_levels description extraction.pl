#!/usr/bin/perl

use strict;
use DBI; #load module
use lib 'C:\\public_html\\Unsupervised';
use SentenceSpliter;

my $i = 0;
my $sentence_id = 0;
my $how_many_words = 0;
my $matched_words_percentage_in_a_sentence = 0;

my @organ_names;
my $match = 0;
my $paragraph = 0;

#################################################################################################################
###########                                collecting organ names                                     ###########
#################################################################################################################

#connect database
my $dbh = DBI -> connect( "DBI:mysql:database=ants;host=localhost", "termsuser", "termspassword", {'RaiseError' => 1} );

#execute SELECT query
my $sth = $dbh -> prepare("SELECT distinct tag FROM sentence ORDER BY tag asc");
$sth -> execute();

while( my $ref = $sth -> fetchrow_hashref()) { #print values
	  $organ_names[$i] = $ref->{'tag'};
	  $i++;
}

#clean up
$dbh -> disconnect();

##################################################################################################################

foreach (@organ_names){
	@organ_names = grep {!($_ eq "[parenttag]" or $_ =~ /^[a-z]$/i)} @organ_names;
}


my @dir_contents; 

my $dir_to_open = "C:\\Users\\Kaiyin\\Desktop\\Plain_text"; 
my $file; #file name

opendir(DIR,$dir_to_open) || die("Cannot open directory !\n"); 
@dir_contents = readdir(DIR);
closedir(DIR);

foreach $file (@dir_contents){ #parse all the files in Plain_text

	my $j = 0;
	my $sentence_count = 0;
	
    if(!(($file eq ".") || ($file eq ".."))){
		print $file."\n";
		
		open (MYFILE, "C:\\Users\\Kaiyin\\Desktop\\Plain_text\\$file") || die ("Could not open the file.");
		my @lines = <MYFILE>; #paragraph -> read as lines
		@lines = grep {!($_ =~ /^(\s+)?$/)} @lines;
		foreach (@lines){
			chomp;
		}
		close(MYFILE);
		
		open (MYFILE, ">C:\\Users\\Kaiyin\\Desktop\\Description2\\$file") || die ("Could not open the file.");
	
LOOP:	for ($j; $j < @lines; $j++) {
			$lines[$j] =~ s/^\s*//;
			my $sen_start_with_organ_names = 0;
		    my $sentence = 0;
			
			my @sentences = SentenceSpliter::get_sentences($lines[$j]); #line -> sentences
			my $how_many_sentences = @sentences;
			my $k = 0;
					
			for($k; $k < @sentences; $k++){
			    $match = 0;
				$sen_start_with_organ_names = 0;
				$sentence_count = $sentence_count + 1;
				
			    my @words = split(/ /,$sentences[$k]); #sentences -> words
				my $w = 0;
				
				for($w; $w < @words; $w++){
				
					$how_many_words = @words;
				    foreach (@organ_names){
					  
						if ($words[$w] =~ /\b$_\b/i){
						    $match ++;
							
							if ($w == 0){						 
								$sen_start_with_organ_names++;
							}
											
						}
					}
				}
				
	            ##################################################################################################################
				##########                         	  write database (sentence table)                                 ############
                ##################################################################################################################				
						
				
				$matched_words_percentage_in_a_sentence = $match/$how_many_words;			
				
				######################################## base level extraction ##################################
				#if ($matched_words_percentage_in_a_sentence > 0){
				 #   $paragraph ++;
					
				#	print MYFILE $lines[$j]."\n\n";
				#	my $sourceID = $file.$j;
					
					#connect database
				#	my $dbh = DBI -> connect( "DBI:mysql:database=ants;host=localhost", "termsuser", "termspassword", {'RaiseError' => 1} );
				
					#execute SELECT query
				#	$lines[$j] =~ s/[\'\"]/\'\'/g;
				#	my $q = "INSERT INTO description VALUES (\"".$sourceID."\",\"".$lines[$j]."\",'N','Y','N','N')";
					
					#print $q."\n";
				#	my $sth = $dbh -> prepare($q);
				#	$sth -> execute();
                
					#clean up
				#	$dbh -> disconnect();

				#	next LOOP;
				#}	
				######################################## base level extraction ##################################
				
				
				######################################## 1 level extraction ##################################
				#if($matched_words_percentage_in_a_sentence > 0){
				#   $sentence++;
				#}
				
				######################################## 2 level extraction ##################################
				if($matched_words_percentage_in_a_sentence > 0){
				   $sentence++;
				}			
				
			    ##################################################################################################################		
			}
			
			######################################## 1 level extraction ##################################
			#if($sentence/@sentences >= 0.5){
			#  print MYFILE $lines[$j]."\n\n";
			 #  $paragraph++;
			 #  my $sourceID = $file.$j;
			   
			   	#connect database
			#	my $dbh = DBI -> connect( "DBI:mysql:database=ants;host=localhost", "termsuser", "termspassword", {'RaiseError' => 1} );
				
				#execute SELECT query
			#	$lines[$j] =~ s/[\'\"]/\'\'/g;
			#	my $q = "UPDATE description SET 1st_level = 'Y' where SourceID = '$sourceID'";
					
			#	my $sth = $dbh -> prepare($q);
			#	$sth -> execute();
                
				#clean up
			#	$dbh -> disconnect();
			#}
			######################################## 1 level extraction ##################################
			
			
			######################################## 2 level extraction ##################################
			if ( (@sentences > 4 && $sentence/@sentences >= 0.5) or (@sentences <= 4 && $sentence/@sentences >= 0.75)){
			    print MYFILE $lines[$j]."\n\n";
			   $paragraph++;
				my $sourceID = $file.$j;
				
				#connect database
			   	my $dbh = DBI -> connect( "DBI:mysql:database=ants;host=localhost", "termsuser", "termspassword", {'RaiseError' => 1} );
				
				#execute SELECT query
				$lines[$j] =~ s/[\'\"]/\'\'/g;
				my $q = "UPDATE description SET 2nd_level = 'Y' where SourceID = '$sourceID'";
					
				my $sth = $dbh -> prepare($q);
				$sth -> execute();
                
				#clean up
				$dbh -> disconnect();
				
			}
			######################################## 2 level extraction ##################################
			
		}	
		close MYFILE;

	}

}
print $paragraph;