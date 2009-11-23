#!/usr/bin/perl
use strict;
use DBI;
use lib 'C:\\public_html\\Unsupervised';
use SentenceSpliter;

sub median { 
	my @pole = @_;
    my $pole = scalar @pole;

    @pole = sort { $a <=> $b } @pole;

    if( ($pole % 2) == 1 ) {
        return $pole[(($pole+1)/2)-1];
    } 
	else {
        return ($pole[($pole/2)-1] + $pole[$pole/2])/2;
    }
}

my @stop_words = ( #removed (the,a,an) and (is,are)
"about", "above", "across", "after", "afterwards", "again", "against", "all", "almost", "alone", "along", "already", "also", "although", 
"always", "am", "among", "amongst", "amoungst", "and", "another", "any", "anyhow", "anyone", "anything", "anyway", "anywhere", "around", "as", "at", "be", "been", "before", "behind", "being", "below", "beside", "besides", "between", "beyond", "both", "but", "by", "can", "cannot", "cant", "co", "could", "couldn't", "de", "do", "done", "down", "due", "during", "each", "eg", "eight", "either", "eleven", "else", "elsewhere", "etc", "even", "ever", "every", "everyone", "everything", "everywhere", "few", "fifteen", "fify", "first", "five", "for", "forty", "four", "from", "front", "get", "go", "had", "has", "hasn't", "have", "he", "hence", "her", "here", "hereafter", "hereby", "herein", "hereupon", "hers", "herself", "him", "himself", "his", "how", "however", "hundred", "i", "i.e.", "if", "in", "inc", "indeed", "into", "it", "its", "itself", "ltd", "may", "me", "might", "more", "moreover", "most", "mostly", "move", "much", "must", "my", "myself", "neither", "never", "nevertheless", "next", "nine", "no", "nobody", "none", "noone", "nor", "not", "nothing", "now", "nowhere", "of", "off", "on", "one", "onto", "or", "other", "others", "otherwise", "our", "ours", "ourselves", "out", "over", "put", "re", "seemed", "seeming", "seems", "she", "should", "since", "six", "sixty", "so", "some", "somehow", "someone","something", "sometime", "sometimes", "somewhere", "still", "such", "ten", "than", "that","their", "them", "themselves","then", "thence", "there", "thereafter", "thereby", "therefore", "therein", "thereupon", "these", "they", "third", "this", "those", "though", "three", "through","throughout", "thru", "thus", "to", "together", "too", "top", "twelve", "twenty", "two", "un", "under", "until", "up", "upon", "us", "very", "via", "was", "we", "well", "were", "what", "whatever", "when", "whence", "whenever", "where", "whereafter", "whereas", "whereby", "wherein", "whereupon", "wherever", "whether", "which", "while", "who", "whoever", "whole", "whom", "whose", "why", "will", "with", "within", "without", "would", "yet", "you", "your", "yours", "yourself", "yourselves");

my @dir_contents; 
my $dir_to_open = "C:\\Users\\Kaiyin\\Desktop\\Plain_text"; 
my $plain_text;
my $bd_id = 0;

opendir(DIR,$dir_to_open) || die("Cannot open directory !\n"); 
@dir_contents= readdir(DIR);
closedir(DIR);

foreach $plain_text (@dir_contents){ 

  if(!(($plain_text eq ".") || ($plain_text eq ".."))){
  
  	my $count = 1;
	my $i = 0;
	my $description = 0;

    open (MYFILE, "C:\\Users\\Kaiyin\\Desktop\\Plain_text\\$plain_text") || die ("Could not open file");

    my @lines = <MYFILE>;
	@lines = grep {!($_ =~ /^(\s+)?$/)} @lines; #remove empty lines
    foreach (@lines) {
       chomp;
    }
	close(MYFILE);

	#open (MYFILE, "C:\\Users\\Kaiyin\\Desktop\\temp\\$plain_text") || die ("Could not open file");
	for($i; $i < @lines; $i++){#for each paragraph
		#$i = 7;
		my $length = 0;
		my $words_not_found = 0;
		my $Capital = 0;
		my $stop_words = 0;
		my $figure = 0;
		my $punc = 0;
		my $noun = 0;
		my $verb = 0;
		my $adj = 0;
		my $adv = 0;
		my $articles = 0;
		my $isare = 0;
		my $url_email = 0;
		my $unknown = 0;
		my $j = 0;
		my $unknown = 0;
	
		my $punc_percentage = 0;
		my $stop_words_percentage = 0;
		my $figure_percentage = 0;
		my $Capital_percentage = 0;
		my $noun_percentage = 0;
		my $verb_percentage = 0;
		my $adj_percentage = 0;
		my $adv_percentage = 0;
		my $url_email_percentage = 0;
		my $not_found_percentage = 0;
		my $articles_percentage = 0;
		my $isare_percentage = 0;
		my $unknown_percentage = 0;
		my @sentences_length;
		my @sentences;
		my $sum = 0;
		my $sd = 0;

		$lines[$i] =~ s/\b(Fig|Figs|Figure|Figures|Table|Tables)\b//gi;
		my $line_copy = $lines[$i];#make a copy of the current line for later print
		
		if ($lines[$i] =~ /\s+\d+(\.\d(\w)*)*/gi){
			$figure += $lines[$i] =~ s/\s+\d+(\.\d(\w)*)*/ /gi;
			$lines[$i] =~ s/\s+\d+(\.\d(\w)*)*/ /gi;
		}
		$lines[$i] =~ s/\s+/ /g;
		$lines[$i] =~ s/^\s*//;
		$lines[$i] =~ s/\s*\n$//;
		
		@sentences = SentenceSpliter::get_sentences($lines[$i]); #line -> sentences

		foreach (@sentences) {
			my @sentences_words = split (/ /,$_);	
			my $len = @sentences_words."\n";
			push (@sentences_length, $len);
		}
		my $median = median(@sentences_length)."\n";
		
		foreach (@sentences_length){
			$sum += abs(scalar($_)-$median);
		}
		if (scalar(@sentences) != 0){
			$sd = $sum/scalar(@sentences);
		}
			
		$punc += $lines[$i] =~ s/[-,;*&:()\?±#'’°"”]+|(\.$)/ /g; #punctuation
		$lines[$i] =~ s/(\s*e\.g\.\s*)|(et\s*al\.)|\bamp\b|\b(I|II|III|IV|V|VI|VII|VIII|IX|X|m|cm)\b/ /gi;
	
		my @words = split(/ /,$lines[$i]);
		my $length = @words;

		if ($length < 200){#if=1
			next;
		}		
		
		#print MYFILE "Line $count:\n";	
		$count = $count + 1;
		#print MYFILE "Total Words: $length\n";
		#print MYFILE "Sentences Length Median:$median";
		#print MYFILE "Sentences Length Standard Deviation:$sd\n";
	
LOOP:  	for($j; $j < $length; $j++){ #for each word

			if ($words[$j] =~ /(www|http:\/\/|@)\.+\w+\d*/){
				$url_email++;
				#print MYFILE $words[$j]."URL".$url_email."\n";
				next LOOP;			
			}
			
			if ($words[$j] =~ /\w*[-,;*&:()\?±#'’°"”\.]+\w*/g){#punctuation
				$punc += $words[$j] =~ s/[-,;*&:()\?±#'’°"”\.]/ /g;
			}
			
			foreach (@stop_words){
				if ($words[$j] =~ /\b$_\b/i){
					$stop_words++;
					next LOOP;
				}
			}
			
			if ($words[$j] =~ /\b(a|an|the)\b/ig){#articles
				$articles++;
				next LOOP;
			}
		    
			if ($words[$j] =~ /\b(am|is|are|was|were|be|being|been)\b/ig){#link verbs
				$isare++;
				next LOOP;
			}
		
			if ($words[$j] =~ /(\d[-%]*)+/g){ #figures
				$figure++;
				next LOOP;
			}
			
			if ($words[$j] =~ /\w+/g){
				    
				if ($words[$j] =~ /^[A-Z]/) {
						$Capital++;
				}

				if (my @result = `wn $words[$j] -over`){
					my @pos = split(/ /,$result[1]);
						
					if ($pos[2] =~ /\bnoun\b/i){
						$noun++;
						next LOOP;
					}
	
					if ($pos[2] =~ /\bverb\b/i){
						$verb++;
						next LOOP;
					}
	
					if ($pos[2] =~ /\badj\b/i){
						$adj++;
						next LOOP;
					}
	
					if ($pos[2] =~ /\badv\b/i){
						$adv++;
						next LOOP;
					}
				}		
			   
				else {
					$words_not_found++;
				}	           
			}
			
			else {
				$unknown++;
			}			
		}
		
		$punc_percentage = ($punc / $length)*100;
		$unknown_percentage = ($unknown / $length)*100;
		$stop_words_percentage = ($stop_words/$length)*100;
		$figure_percentage = ($figure/$length)*100;
		$Capital_percentage = ($Capital/$length)*100;
		$noun_percentage = ($noun/$length)*100;
		$verb_percentage = ($verb/$length)*100;
		$adj_percentage = ($adj/$length)*100;
		$adv_percentage = ($adv/$length)*100;
		$url_email_percentage = ($url_email/$length)*100;
		$not_found_percentage = ($words_not_found/$length)*100;		
		$articles_percentage = ($articles/$length)*100;
		$isare_percentage = ($isare/$length)*100;
			
		if (($length >= 250) && ($articles_percentage <= 5) && ($isare_percentage <= 1) && ($figure_percentage <= 10) && ($Capital_percentage <= 15) && ($sd >= 5)){
			    $description++;
				$bd_id++;
				open (MYFILE, ">C:\\Users\\Kaiyin\\Desktop\\Description1\\[$description].$plain_text") || die ("Could not open file");
				print MYFILE $line_copy;			
				close MYFILE;
				
				##################################################################################################################
				##########                         	  write database (sentence table)                                 ############
                ##################################################################################################################				
							
				#connect database
				my $dbh = DBI -> connect( "DBI:mysql:database=ants;host=localhost", "termsuser", "termspassword", {'RaiseError' => 1} );

				#execute SELECT query
				my $sth = $dbh -> prepare("INSERT INTO base_description VALUES ('$bd_id', '$plain_text',$i)");#i从0开始计
				$sth -> execute();

				#clean up
				$dbh -> disconnect();
				
			    ##################################################################################################################
				
			}

    }
  
    close (MYFILE);
			
  }
 
}