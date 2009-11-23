#!/usr/bin/perl

use strict;
use Data::Dumper;
use XML::Simple;
use LWP::Simple; #important

my $url= "http://plazi.cs.umb.edu:8080/exist/rest/db/taxonx_docs";
my $file = get($url);
my $parse = XMLin($file);

my $string = Dumper($parse);
open (MYFILE, ">C:\\public_html\\file1.txt") || die ("Could not open file");
print MYFILE ($string);
close(MYFILE);

open (MYFILE, "C:\\public_html\\file1.txt") || die ("Could not open file");
my @lines = <MYFILE>;
close(MYFILE);

my $i = 0;

for ($i ;; $i++){
   my $j = 0;
   my $count = 0;

  if($lines[$i] =~ /.*xml.*/){
    $lines[$i] =~ s/^\s*'//;
    $lines[$i] =~ s/' => {\s*$//;
  
    my $url2="http://plazi.cs.umb.edu:8080/exist/rest/db/taxonx_docs/$lines[$i]";
    my $document = get($url2);
    open (MYFILE, ">C:\\Users\\Kaiyin\\Desktop\\Plazi\\$lines[$i].txt") || die ("Could not open file");
    print MYFILE ($document);
    close (MYFILE);
  
    open (MYFILE, "C:\\Users\\Kaiyin\\Desktop\\Plazi\\$lines[$i].txt") || die ("Could not open file");
    my @plain_text = <MYFILE>;
    close (MYFILE);

    open (MYFILE, ">C:\\Users\\Kaiyin\\Desktop\\Plain_text\\$lines[$i].txt") || die("Could not open file");
	for ($j; $j < @plain_text; $j++){
		$plain_text[$j] =~ s/[\r\n]+$//g;

	    if ($plain_text[$j] =~ /^\s*<tax:p>/){ #1 <tax:p>
		    if ($plain_text[$j] =~ /<\/tax:p>\s*$/){ #1 <tax:p> one line
			
			    $plain_text[$j] =~ s/\s*(<.*?>)\s*/ /g;			
			    print MYFILE ($plain_text[$j]."\n\n");	
				
		    }

		   else { #2 <tax:p>, multiple lines
				$plain_text[$j] =~ s/[\r\n]+$//g;
				$plain_text[$j] =~ s/\s*(<.*?>)\s*/ /g;
				print MYFILE ($plain_text[$j]);
		        $count = $j;
				
	            until ($plain_text[$count] =~ /<\/tax:p>\s*$/){
				
					$plain_text[$count] =~ s/[\r\n]+$//g;
					$plain_text[$j] =~ s/\s*(<.*?>)\s*/ /g;
					print MYFILE ($plain_text[$count]);
					$count++;
					$j = $count;
		
		        }
				print MYFILE ("\n\n");
	        }	
	    }
    }
	close (MYFILE);
 }
}